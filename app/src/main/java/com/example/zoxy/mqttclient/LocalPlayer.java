package com.example.zoxy.mqttclient;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.SeekBar;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by zoxy on 2017/5/10.
 */

public class LocalPlayer implements MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener,MediaPlayer.OnPreparedListener{


    public MediaPlayer mediaPlayer;
    private SeekBar progress;
    private Timer localTime;
    private ResultExplainer ex = new ResultExplainer();
    public static String songUrl = null;

    public LocalPlayer(SeekBar progress){
        this.progress = progress;
        try{
            this.mediaPlayer = new MediaPlayer();
            this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            this.mediaPlayer.setOnBufferingUpdateListener(this);
            this.mediaPlayer.setOnPreparedListener(this);
            this.mediaPlayer.setOnCompletionListener(this);
            localTime = new Timer();
            localTime.schedule(mTimerTask,0,1000);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    TimerTask mTimerTask = new TimerTask() {
        @Override
        public void run() {
            if(mediaPlayer==null)
                return;
            if (mediaPlayer.isPlaying() && !progress.isPressed()) {
                handleProgress.sendEmptyMessage(0);
            }
        }
    };
    Handler handleProgress = new Handler() {
        public void handleMessage(Message msg) {

            int position = mediaPlayer.getCurrentPosition();
            int duration = 0;
            if(mediaPlayer.isPlaying()){
                duration = mediaPlayer.getDuration();

                if (duration > 0) {
                    long pos = progress.getMax() * position / duration;
                    progress.setProgress((int) pos);
                }
            }
        }
    };

    public void play(){
        mediaPlayer.start();

    }

    public void playURL(String url){
        try{
            if(Globals.NoticeOrNot) {
                songUrl = url;
                mediaPlayer.reset();
                mediaPlayer.setDataSource("http://192.168.1.44:9090/result.mp3");
                mediaPlayer.prepareAsync();
            }else {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(url);
                mediaPlayer.prepareAsync();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void stop(){
        if(mediaPlayer != null){
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if(localTime!=null) {
            localTime.cancel();
        }
    }

    public void pause(){
        mediaPlayer.pause();
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {
        progress.setSecondaryProgress(i);

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        try {
            if(Globals.NoticeOrNot&&songUrl!=""&&songUrl!="null") {
                mediaPlayer.reset();
                mediaPlayer.setDataSource(songUrl);
                mediaPlayer.prepareAsync();

            }else {
                ex.updatePlayState("STOPPED");
                MQTTService.actionSendMSG(Globals.curContext, "NEXT");
            }
            Globals.NoticeOrNot = false;
        }catch(Exception e){
            e.printStackTrace();
        }

    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        mediaPlayer.start();
        ex.updateTotalTime(mediaPlayer.getDuration());
        Log.i("mediaPlayer","onPrepared");
    }


}
