package com.example.zoxy.mqttclient;

import android.content.Context;
import android.media.AudioManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/**
 * Created by zoxy on 2017/3/20.
 */

public class PVSparser {

    public JSONObject getAlertsState(){
        JSONObject alerts = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        try{
            header.put("namespace", "Alerts");
            header.put("name", "AlertsState");
            payload.put("allAlerts","");
            payload.put("activeAlerts","");
            alerts.put("header",header);
            alerts.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }
        return alerts;
    }

    public JSONObject getPlaybackState(){
        JSONObject PlaybackState = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        try{
            header.put("namespace", "AudioPlayer");
            header.put("name", "PlaybackState");
            payload.put("offsetInMilliseconds","long");
            payload.put("playerActivity","string");
            PlaybackState.put("header",header);
            PlaybackState.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }
        return PlaybackState;
    }

    public JSONObject getVolumeState(Context mContext){
        JSONObject PlaybackState = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        AudioManager mAudioMangager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);

        try{
            header.put("namespace", "Speaker");
            header.put("name", "VolumeState");
            payload.put("volume",String.valueOf(mAudioMangager.getStreamVolume(AudioManager.STREAM_MUSIC)));
            payload.put("muted",String.valueOf(mAudioMangager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0));
            PlaybackState.put("header",header);
            PlaybackState.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }
        return PlaybackState;
    }

    public JSONObject getSpeechState(Context mContext){
        JSONObject PlaybackState = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        AudioManager mAudioMangager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        try{
            header.put("namespace", "SpeechSynthesizer");
            header.put("name", "SpeechState");
            payload.put("offsetInMilliseconds","long");
            payload.put("playerActivity",String.valueOf(mAudioMangager.isMusicActive()));
            PlaybackState.put("header",header);
            PlaybackState.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }
        return PlaybackState;
    }

    public JSONArray getContext(Context mContext){
        JSONArray context = new JSONArray();
        try{
            context.put(0,getAlertsState());
            context.put(1,getPlaybackState());
            context.put(2,getVolumeState(mContext));
            context.put(3,getSpeechState(mContext));
        }catch(Exception e){
            e.printStackTrace();
        }
        return context;
    }

    public JSONObject getSpeechRecognizerEvent(String name, String namespace, Map<String,String> params){

        JSONObject event = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        try{
            header.put("namespace", namespace);
            header.put("name", name);
            header.put("messageId", params.get("messageID"));
            header.put("dialogRequestId", params.get("dialogRequestID"));
            if(name.equalsIgnoreCase("Recognize")){
                payload.put("profile", "CLOSE_TALK");
                payload.put("format", "AUDIO_L16_RATE_16000_CHANNELS_1");
            }
            event.put("header",header);
            event.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }

        return event;
    }

    public JSONObject getSpeechSynthesizerEvent(String name, String namespace, Map<String,String> params){
        return getNopayloadEvent(name,namespace,params);
    }

    public JSONObject getTimersEvent(String name, String namespace, Map<String,String> params){
        return getNopayloadEvent(name,namespace,params);
    }

    public JSONObject getAudioPlayerEvent(String name, String namespace, Map<String,String> params) {

        JSONObject event = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        try {
            header.put("namespace", namespace);
            header.put("name", name);
            header.put("messageId", params.get("messageID"));
            if (name.equalsIgnoreCase("PlaybackFailed")) {
                payload.put("currentPlaybackState", new JSONObject().put("offsetInMilliseconds", params.get("offsetInMilliseconds")).put("playerActivity", params.get("playerActivity")));
                payload.put("error", new JSONObject().put("type", params.get("errortype")).put("message", params.get("message")));
            } else if (name.equalsIgnoreCase("PlaybackQueueCleared")) {

            } else {
                payload.put("offsetInMilliseconds", params.get("offsetInMilliseconds"));
            }
            event.put("header", header);
            event.put("payload", payload);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return event;
    }

    public JSONObject getPlaybackControllerEvent(String name, String namespace, Map<String,String> params){
        return getNopayloadEvent(name,namespace,params);
    }

    public JSONObject getSpeakerVolumeEvent(String name, String namespace, Map<String,String> params){

        JSONObject event = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        try{
            header.put("namespace", namespace);
            header.put("name", name);
            header.put("messageId", params.get("messageID"));
            payload.put("volume",params.get("volume"));
            payload.put("muted",params.get("muted"));
            event.put("header",header);
            event.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }

        return event;
    }

    public JSONObject getSettingsEvent(String name, String namespace, Map<String,String> params){

        JSONObject event = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        try{
            header.put("namespace", namespace);
            header.put("name", name);
            header.put("messageId", params.get("messageID"));
            JSONArray settings = new JSONArray();
            settings.put(0,new JSONObject().put("key",params.get("key")).put("value",params.get("value")));
            payload.put("settings",settings);
            event.put("header",header);
            event.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }

        return event;
    }

    public JSONObject getSystemEvent(String name, String namespace, Map<String,String> params){

        JSONObject event = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        try{
            header.put("namespace", namespace);
            header.put("name", name);
            header.put("messageId", params.get("messageID"));

            if(name.equalsIgnoreCase("ExceptionEncountered")){

            }else if(name.equalsIgnoreCase("UserInactivityReport")){

            }
            event.put("header",header);
            event.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }

        return event;
    }

    public JSONObject getNopayloadEvent(String name, String namespace, Map<String,String> params){

        JSONObject event = new JSONObject();
        JSONObject header = new JSONObject();
        JSONObject payload = new JSONObject();
        try{
            header.put("namespace", namespace);
            header.put("name", name);
            header.put("messageId", params.get("messageID"));
            event.put("header",header);
            event.put("payload",payload);
        }catch(Exception e){
            e.printStackTrace();
        }

        return event;
    }

    public JSONObject getEvent(String name, String namespace, Map<String,String> params){
        if(namespace.equalsIgnoreCase("SpeechRecognizer")){
            return getSpeechRecognizerEvent(name, namespace, params);
        }else if(namespace.equalsIgnoreCase("SpeechSynthesizer")){
            return getSpeechSynthesizerEvent(name, namespace, params);
        }else if(namespace.equalsIgnoreCase("Timers")){
            return getTimersEvent(name, namespace, params);
        }else if(namespace.equalsIgnoreCase("AudioPlayer")){
            return getAudioPlayerEvent(name, namespace, params);
        }else if(namespace.equalsIgnoreCase("PlaybackController")){
            return getPlaybackControllerEvent(name, namespace, params);
        }else if(namespace.equalsIgnoreCase("SpeakerVolume")){
            return getSpeakerVolumeEvent(name, namespace, params);
        }else if(namespace.equalsIgnoreCase("Settings")){
            return getSettingsEvent(name, namespace, params);
        }else if(namespace.equalsIgnoreCase("System")){
            return getSystemEvent(name, namespace, params);
        }else{
            return null;
        }
    }



}
