package com.example.zoxy.mqttclient;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.example.zoxy.mqttclient.MainActivity.updateUiHandler;


public class MQTTService extends Service {

    public static MQTTService service = null;

    private String host = "tcp://192.168.1.44:61613";
//    private String host = "tcp://test.amber-link.com:1883";

    private Handler handler;

    private MqttClient client;

    private String myTopic = "yipai_test/topic";
//    private String myTopic ="android/test";

    private MqttConnectOptions options;
//    private PVSparser pvSparser = new PVSparser();

    private ScheduledExecutorService scheduler;

    private static final String		ACTION_START = "SERVICE_START";
    private static final String		ACTION_STOP = "SERVICE_STOP";
    private static final String		ACTION_CANCEL = "SERVICE_CANCEL";
    private static final String		ACTION_KEEPALIVE = "SERVICE_KEEPALIVE";
    private static final String		ACTION_CREATE = "SERVICE_CREATE";
    private static final String     ACTION_SEND = "SERVICE_SEND";
    private static final long		KEEP_ALIVE_INTERVAL = 1000 * 60 * 28;
    //private static final long		KEEP_ALIVE_INTERVAL = 1000;

    private int                     recordbuf_size;
    private AudioRecord             mAudioRecord;
    private byte[]                  input_bytes;
    private boolean                 isRecording;
    private Thread                  recordThread;


    public MQTTService() {
    }


    public static void actionStart(Context ctx) {
        Intent i = new Intent(ctx, MQTTService.class);
        i.setAction(ACTION_START);
        ctx.startService(i);
    }

    public static void actionStop(Context ctx) {
        Intent i = new Intent(ctx, MQTTService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    public static void actionCancel(Context ctx) {
        Intent i = new Intent(ctx, MQTTService.class);
        i.setAction(ACTION_CANCEL);
        ctx.startService(i);
    }

    public static void actionCreate(Context ctx) {
        Intent i = new Intent(ctx, MQTTService.class);
        i.setAction(ACTION_CREATE);
        ctx.startService(i);
    }

    public static void actionSendMSG(Context ctx, String msg) {
        Intent i = new Intent(ctx, MQTTService.class);
        i.setAction(ACTION_SEND);
        try{
            i.putExtra("msg",msg);
            ctx.startService(i);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate(){
        super.onCreate();
        init();
        service = this;
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == 2) {
                    System.out.println("连接成功");
                    MainActivity.UpdateConnectState state = new MainActivity.UpdateConnectState(true);
                    updateUiHandler.post(state);
                    try {
//                        client.subscribe(myTopic, 1);
//                        client.subscribe(myTopic+"/keepalive",1);
                        client.subscribe(myTopic+"/song",1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (msg.what == 3) {
                    MainActivity.UpdateConnectState state = new MainActivity.UpdateConnectState(false);
                    updateUiHandler.post(state);
                    System.out.println("连接失败，系统正在重连");
                }
            }
        };

        startReconnect();
        registerReceiver(mConnectivityChanged, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    public void onStart(Intent intent, int startID){
        super.onStart(intent, startID);
        if(intent.getAction().equals(ACTION_STOP) == true){
            stop();
            publishString(myTopic,"end");
        } else if(intent.getAction().equals(ACTION_START) == true){
            start();
        } else if(intent.getAction().equals(ACTION_KEEPALIVE) == true){
            keepalive();
        } else if(intent.getAction().equals(ACTION_CANCEL) == true){
            stop();
            publishString(myTopic,"cancel");
        } else if(intent.getAction().equals(ACTION_SEND) == true){
            JSONObject pvs = new JSONObject();
            String msg = intent.getStringExtra("msg");
            try {
                pvs.put("cmd", msg);
                if(msg.equalsIgnoreCase("PROGRESSCHANGE")){
                    pvs.put("progress",Globals.playProgress);
                }else if(msg.equalsIgnoreCase("VOLUMECHANGE")){
                    pvs.put("volume",Globals.volume);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
            publishString(myTopic,pvs.toString());
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        publishString(myTopic,"cancel");
        isRecording = false;
        try {
            if(client.isConnected()) {
                scheduler.shutdown();
                stopKeepAlives();
                client.disconnect();
                client = null;
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static MQTTService getMQTTservice (){
        return service;
    }

    private void startReconnect() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                if (!client.isConnected()&& isConnectIsNomarl()) {
                    connect();
                }
            }
        }, 0 * 1000, 10 * 1000, TimeUnit.MILLISECONDS);
    }

    private void init() {
        try {
            //host为主机名，test为clientid即连接MQTT的客户端ID，一般以客户端唯一标识符表示，MemoryPersistence设置clientid的保存形式，默认为以内存保存
            client = new MqttClient(host, Globals.clientID,
                    new MemoryPersistence());

            //MQTT的连接设置
            options = new MqttConnectOptions();
            //设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，这里设置为true表示每次连接到服务器都以新的身份连接
            options.setCleanSession(true);
            // 设置超时时间 单位为秒
            options.setConnectionTimeout(10);
            // 设置会话心跳时间 单位为秒 服务器会每隔1.5*20秒的时间向客户端发送个消息判断客户端是否在线，但这个方法并没有重连的机制
            options.setKeepAliveInterval(20);
            //设置回调
            options.setUserName("admin");
            options.setPassword("password".toCharArray());
            client.setCallback(new MqttCallback() {

                @Override
                public void connectionLost(Throwable cause) {
                    //连接丢失后，一般在这里面进行重连
                    System.out.println("connectionLost----------");
                    cause.printStackTrace();
                    MainActivity.UpdateConnectState state = new MainActivity.UpdateConnectState(false);
                    updateUiHandler.post(state);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    //publish后会执行到这里
                    System.out.println("deliveryComplete---------"
                            + token.isComplete());
                }

                @Override
                public void messageArrived(String topicName, final MqttMessage message)
                        throws Exception {
                    //subscribe后得到的消息会执行到这里面
                    System.out.println("payload length : "+message.toString().length());
                    System.out.println("messageArrived----------");
   //                 System.out.println(topicName + "---" + payload);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ResultExplainer ex = new ResultExplainer();
                            ex.explainPVSto(message.toString());
                        }
                    }).run();

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connect() {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    client.connect(options);
                    Message msg = Message.obtain();
                    msg.what = 2;
                    handler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = Message.obtain();
                    msg.what = 3;
                    handler.sendMessage(msg);
                }
            }
        }).start();
    }

    private synchronized void start(){

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if(client.isConnected()){
                    //stop keeping alives
                    stopKeepAlives();
                    // Recording params initial
                    recordbuf_size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,recordbuf_size);
                    input_bytes = new byte[recordbuf_size];
//                    String PVS = String.valueOf(pvSparser.getPVS("Recognize","SpeechRecognizer",null));
//                    publishString(myTopic,PVS);
                    isRecording = true;
                    recordThread = new record2push();
                    recordThread.start();
                }else{
                    startReconnect();
                }
            }
        });
        thread.run();

    }

    private synchronized void stop(){
        isRecording = false;
        startKeepAlives();
    }

    class record2push extends Thread {

        @Override
        public void run() {
            // TODO Auto-generated method stub

            try {
//                File www = new File(Environment.getExternalStorageDirectory().getPath(),"wavtest.raw");
//                File sss = new File(Environment.getExternalStorageDirectory().getPath(),"wavtest.wav");
//                FileOutputStream fos = new FileOutputStream(www);
                byte[] bytes;
                mAudioRecord.startRecording();
                while (isRecording) {
                    int bytesize = mAudioRecord.read(input_bytes, 0, recordbuf_size);
                    System.out.println("--------------"+bytesize);
                    bytes = new byte[bytesize];
                    System.arraycopy(input_bytes, 0, bytes, 0, bytesize);
                    publishBytes(bytes);
                    System.out.println(System.currentTimeMillis());
//                    System.out.print(bytes.toString());
//                    fos.write(bytes);
                }
                mAudioRecord.release();
//                fos.flush();
//                fos.close();
//                new AudioRecordThread().run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    class AudioRecordThread extends Thread {
        @Override
        public void run() {
            copyWaveFile(Environment.getExternalStorageDirectory().getPath()+"/wavtest.raw", Environment.getExternalStorageDirectory().getPath()+"/wavtest.wav");//给裸数据加上头文件
        }
    }

    public synchronized void publishAlivenote(final String msg){

        final String topic = myTopic+"keepalive";
        final Integer qos = 1;
        final Boolean retained = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.publish(myTopic+"keepalive", msg.getBytes(), qos.intValue(), retained.booleanValue());
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }).start();


    }

    public synchronized void publishString(final String topic, final String  msg){
        final Integer qos = 2;
        final Boolean retained = false;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                    client.publish(topic, msg.getBytes(), qos.intValue(), retained.booleanValue());
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
            }).start();



    }

    public synchronized void  publishBytes(final byte[] bytes){

        final String topic = myTopic;
        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    MqttMessage msg = new MqttMessage();
                    msg.setPayload(bytes);
                    client.publish(topic,msg);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }).start();


    }

    private boolean isConnectIsNomarl() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info != null && info.isAvailable()) {
            String name = info.getTypeName();
            Log.i("network", "MQTT当前网络名称：" + name);
            return true;
        } else {
            Log.i("network", "MQTT 没有可用网络");
            return false;
        }
    }

    // Schedule application level keep-alives using the AlarmManager
    private void startKeepAlives() {
        Intent i = new Intent();
        i.setClass(this, MQTTService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager)getSystemService(ALARM_SERVICE);
        alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + KEEP_ALIVE_INTERVAL,
                KEEP_ALIVE_INTERVAL, pi);
    }

    // Remove all scheduled keep alives
    private void stopKeepAlives() {
        Intent i = new Intent();
        i.setClass(this, MQTTService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        AlarmManager alarmMgr = (AlarmManager)getSystemService(ALARM_SERVICE);
        alarmMgr.cancel(pi);
    }

    private synchronized void keepalive(){
        try{
            if(client.isConnected()){
                publishAlivenote("a");
            }else{
                startReconnect();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private BroadcastReceiver mConnectivityChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeInfo = manager.getActiveNetworkInfo();

            if (activeInfo!=null) {
                startReconnect();
            } else  {
                try {
                    stopKeepAlives();
                    client.disconnect();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void copyWaveFile(String inFilename, String outFilename) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = 16000;
        int channels = 1;
        long byteRate = 16 * 16000 * channels / 8;
        byte[] data = new byte[recordbuf_size];
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }
            in.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws Exception {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }

}
