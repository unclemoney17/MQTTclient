package com.example.zoxy.mqttclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {

    private Button record_btn;
    private static SeekBar progress;
    private static ToggleButton player_btn;
    private static ImageView album_image;
    private static TextView connection_state;
    private static TextView song_time;
    private static RelativeLayout main;

    AudioManager audioManager;
    MyVolumeReceiver myVolumeReceiver = new MyVolumeReceiver();
    IntentFilter filter = new IntentFilter();

    static ResultExplainer ex;

    static Timer timer = new Timer();
    static LocalPlayer localPlayer;

    private static float DownY;

    public static Handler updateUiHandler = new Handler();

    public static final String ACTION = "android.media.VOLUME_CHANGED_ACTION";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Globals.curContext = this;

        main = (RelativeLayout)findViewById(R.id.main);
        record_btn = (Button) findViewById(R.id.start);
        player_btn = (ToggleButton) findViewById(R.id.player);
        progress = (SeekBar) findViewById(R.id.progress);
        album_image = (ImageView) findViewById(R.id.album);
        song_time = (TextView) findViewById(R.id.songtime);
        connection_state = (TextView)findViewById(R.id.connect);

        //注册音量变化监听事件
        audioManager = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
        filter.addAction(ACTION);
        registerReceiver(myVolumeReceiver,filter);

        //开启MQTT服务
        MQTTService.actionCreate(MainActivity.this);

        //record_button设置
        record_btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        DownY = motionEvent.getY();
                        MQTTService.actionStart(MainActivity.this);
                        Toast.makeText(MainActivity.this, "开始录音", Toast.LENGTH_SHORT).show();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(motionEvent.getY() - DownY) > 100) {
                            MQTTService.actionCancel(MainActivity.this);
                            Toast.makeText(MainActivity.this, "取消录音", Toast.LENGTH_SHORT).show();
                        } else {
                            MQTTService.actionStop(MainActivity.this);
                            Toast.makeText(MainActivity.this, "完成录音", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }
        });


        //player_button设置
        player_btn.setText("PLAY");
        player_btn.setTextOff("PAUSED");
        player_btn.setTextOn("PLAYING");
        player_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (player_btn.isChecked()) {
                    Globals.playState = "PLAYING";
                    MQTTService.actionSendMSG(MainActivity.this, "PLAY");
                    if(Globals.boxConnect) {
                        if (Globals.boxConnect&&progress.getProgress() != progress.getMax()) {
                            int step = (int) (1000 * (Globals.totalTime / 100.0));
                            timer = new Timer();
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    int newPro = progress.getProgress() + 1;
                                    progress.setProgress(newPro);
                                }
                            }, step, step);
                        }
                    }else{
                        if(localPlayer.mediaPlayer!=null) {
                            localPlayer.play();
                        }
                    }
                } else {
                    Globals.playState = "PAUSED";
                    MQTTService.actionSendMSG(MainActivity.this, "PAUSE");
                    if(Globals.boxConnect) {
                        if (timer != null)
                            timer.cancel();
                    }else{
                        if(localPlayer.mediaPlayer!=null) {
                            localPlayer.pause();
                        }
                    }

                }
            }
        });

        //ProgressBar设置
        progress.setEnabled(true);
        progress.setMax(100);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int pro;
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
               if(!Globals.boxConnect&&localPlayer.mediaPlayer!=null) {
                   pro = i * localPlayer.mediaPlayer.getDuration()
                           / seekBar.getMax();
                   Globals.playProgress = i;
               }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(timer!=null) {
                    timer.cancel();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(Globals.boxConnect){
                    Globals.playProgress = seekBar.getProgress() * Globals.totalTime / 100;
                    MQTTService.actionSendMSG(MainActivity.this, "PROGRESSCHANGE");

                    int min = (int) Globals.playProgress / 60;
                    int sec = (int) Globals.playProgress % 60;
                    Toast.makeText(MainActivity.this, min + ":" + sec, Toast.LENGTH_SHORT).show();
                    if(Globals.boxConnect&& Globals.playState.contains("PLAY")&&progress.getProgress()!=progress.getMax()) {
                        int step = (int) (1000 * (Globals.totalTime / 100.0));
                        timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                int newPro = progress.getProgress() + 1;
                                progress.setProgress(newPro);
                                Globals.playProgress = newPro * Globals.totalTime / 100;
                            }
                        }, step, step);
                    }
                }else{
                    if(localPlayer.mediaPlayer!=null)
                        localPlayer.mediaPlayer.seekTo(pro);
                }
            }
        });

        localPlayer = new LocalPlayer(progress);
        ex = new ResultExplainer();
        String src = "https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1493591112275&di=740bce89c3db170c8d8c95794a5f6c79&imgtype=0&src=http%3A%2F%2Fimgq.duitang.com%2Fuploads%2Fitem%2F201506%2F07%2F20150607131856_NVXUR.jpeg";
        ex.updateAlbumImage(src);

    }

    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myVolumeReceiver);
        if(localPlayer.mediaPlayer!=null)
            localPlayer.stop();
    }

    //音量变化后操作
    public class MyVolumeReceiver extends BroadcastReceiver{

        public MyVolumeReceiver() {

        }
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ACTION)){
                Globals.volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                MQTTService.actionSendMSG(MainActivity.this, "VOLUMECHANGE");
            }
        }
    }




    //更新专辑图片
    public static class UpdateImage implements Runnable{
        private Bitmap urlResource = null;

        UpdateImage(Bitmap urlResource){
            this.urlResource = urlResource;
        }

        @Override
        public void run() {
            if(urlResource==null){
                return;
            }
            //更新UI
            try {
                album_image.setImageBitmap(urlResource);
                main.postInvalidate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    //更新播放状态
    public static class UpdatePlayState implements Runnable{

        UpdatePlayState(String playState){
            Globals.playState = playState;
        }

        @Override
        public void run() {
            //更新UI
            try {
                if(Globals.boxConnect&&Globals.playState.equalsIgnoreCase("PLAYING")&&progress.getProgress()!=progress.getMax()) {
                    player_btn.setChecked(true);
                    int step = (int) (1000 * (Globals.totalTime / 100.0));
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            int newPro = progress.getProgress() + 1;
                            progress.setProgress(newPro);
                            Globals.playProgress = newPro * Globals.totalTime / 100;
                        }
                    }, step, step);
                }
                else {
                    player_btn.setChecked(false);
                    if(timer!=null) {
                        timer.cancel();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Exception",e.getMessage());
            }
        }

    }

    //更新播放进度
    public static class UpdateProgressBar implements Runnable{

        UpdateProgressBar(long progressTime){
            Globals.playProgress = progressTime;
        }

        @Override
        public void run() {
            //更新UI
            try {
                if(timer!=null) {
                    timer.cancel();
                }
                Globals.playProgress = Globals.playProgress * progress.getMax() / Globals.totalTime ;
                progress.setProgress((int)Globals.playProgress);
                if(Globals.boxConnect&&Globals.playState.equalsIgnoreCase("PLAYING")&&progress.getProgress()!=progress.getMax()) {
                    player_btn.setChecked(true);
                    int step = (int) ((Globals.totalTime / progress.getMax()));
                    timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            int newPro = progress.getProgress() + 1;
                            progress.setProgress(newPro);
                            Globals.playProgress = newPro * Globals.totalTime / progress.getMax();
                        }
                    }, step, step);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Exception",e.getMessage());
            }
        }

    }

    //更新歌曲长度
    public static class UpdateTotalTime implements Runnable{
        UpdateTotalTime(long time){
            Globals.totalTime = time;
            Globals.playProgress = 0;
        }

        @Override
        public void run() {
            song_time.setText(Globals.totalTime/60/1000+":"+ Globals.totalTime/1000%60);
        }
    }

    //更新连接状态
    public static class UpdateConnectState implements Runnable{
        boolean connect;
        UpdateConnectState(boolean connect){
            this.connect = connect;
        }

        @Override
        public void run() {
            if(connect){connection_state.setText("连接状态：已连接");}else{connection_state.setText("连接状态：正在连接");}
        }
    }

    //本地播放歌曲
    public static class PlaySongLocal implements Runnable{
        String songUrl = null;
        PlaySongLocal(String songUrl){

            this.songUrl = songUrl;
            Globals.playProgress = 0;
            Globals.playState = "PLAYING";

        }

        @Override
        public void run() {
            if(songUrl==null){
                return;
            }
            player_btn.setChecked(true);
            progress.setProgress(0);
            if(localPlayer == null)
                localPlayer = new LocalPlayer(progress);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    localPlayer.playURL(songUrl);
                }
            }).run();

        }
    }




}

