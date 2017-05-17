package com.example.zoxy.mqttclient;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.example.zoxy.mqttclient.MainActivity.*;

/**
 * Created by zoxy on 2017/4/30.
 */


public class ResultExplainer {



    public void explainPVS(final String PVSstring){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject PVS = new JSONObject(PVSstring);
                    JSONObject directive;
                    if(PVS.has("directive")) {
                        directive = PVS.getJSONObject("directive");
                    }else{
                        return;
                    }
                    JSONObject header = directive.getJSONObject("header");
                    String namespace = header.getString("namespace");
                    String name = header.getString("name");

                    if (namespace.equalsIgnoreCase("AudioPlayer")){
                        if(name.equalsIgnoreCase("play")){
                            updatePlayState("PLAYING");
                            JSONObject payload = directive.getJSONObject("payload");
                            JSONObject audioItem = payload.getJSONObject("audioItem");
                            JSONObject stream = audioItem.getJSONObject("stream");

                            long progress = payload.getLong("offsetInMilliseconds");
                            updateProgressBar(progress);
                            //如果是开始播放，而不是改变progress
                            if(progress == 0) {
                                String url = stream.getString("imageUrl");
                                long totalTime = stream.getLong("totalTime");
                                updateAlbumImage(url);
                                updateTotalTime(totalTime);
                            }
                        }else if(name.equalsIgnoreCase("stop")){
                            updatePlayState("PAUSED");
                        }else if(name.equalsIgnoreCase("ClearQueue")) {
                            updateProgressBar(0);
                            updatePlayState("STOPPED");
                        }
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }).run();


    }


    public void explainPVSto (final String payload){

                try {

                    JSONObject httpresult = new JSONObject(payload);
                    if(httpresult.has("Song")) {
                        Globals.boxConnect = false;
                        Globals.NoticeOrNot = httpresult.getBoolean("NoticeOrNot");
//                        playResponse("http://192.168.1.44:9090/result.wav");
//                        playSongLocal("http://192.168.1.44:9090/result.mp3","null");
                        playSongLocal(String.valueOf(httpresult.get("Url")), String.valueOf(httpresult.get("Cover")));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
    }

    public void updateAlbumImage(final String src) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (src.equalsIgnoreCase("null")||src == "") {
                    return;
                } else {
                    try {
                        URL url = new URL(src);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setConnectTimeout(5000);
                        connection.setRequestMethod("GET");
                        connection.setDoInput(true);
                        connection.setUseCaches(false);
                        InputStream input = connection.getInputStream();
                        Bitmap urlResource = BitmapFactory.decodeStream(input);
                        UpdateImage uiThread = new UpdateImage(urlResource);
                        updateUiHandler.post(uiThread);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void updatePlayState(String playState){
        UpdatePlayState uiThread = new UpdatePlayState(playState);
        updateUiHandler.post(uiThread);
    }

    public void updateProgressBar(long progress){
        UpdateProgressBar uiThread = new UpdateProgressBar(progress);
        updateUiHandler.post(uiThread);
    }

    public void updateTotalTime(long totalTime){
        UpdateTotalTime uiThread = new UpdateTotalTime(totalTime);
        updateUiHandler.post(uiThread);
    }

    public void playSongLocal(String songUrl, String coverUrl){

        PlaySongLocal uiThread = new PlaySongLocal(songUrl);
        updateUiHandler.post(uiThread);
        updateAlbumImage(coverUrl);
    }


    public void playResponse(final String responseUrl){


        new Thread(new Runnable() {
            int playoffset = 0;
            int buf_size = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            int play_size = buf_size;
            byte[] buf = new byte[play_size];
            AudioTrack mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,16000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,buf_size,AudioTrack.MODE_STREAM );

            @Override
            public void run() {

                try{
                    URL url = new URL(responseUrl);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    InputStream reader = con.getInputStream();

                    mAudioTrack.play();
                    while(true){
                        if(reader.read(buf) == -1)
                            break;
                        mAudioTrack.write(buf,0,play_size);
                        playoffset +=play_size;
                    }
                    mAudioTrack.stop();
                    mAudioTrack.release();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }).run();

    }
}
