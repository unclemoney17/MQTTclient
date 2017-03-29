package com.example.zoxy.mqttclient;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.eclipse.paho.client.mqttv3.MqttMessage;


public class MainActivity extends AppCompatActivity {

    private Button start_btn;
    private ToggleButton light_btn;
    private ToggleButton player_btn;
    private static float DownY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        start_btn = (Button)findViewById(R.id.start);
        player_btn = (ToggleButton)findViewById(R.id.player);

        MQTTService.actionCreate(MainActivity.this);

        start_btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                switch (motionEvent.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        DownY = motionEvent.getY();
                        MQTTService.actionStart(MainActivity.this);
                        Toast.makeText(MainActivity.this,"开始录音",Toast.LENGTH_SHORT).show();
                        break;
                    case MotionEvent.ACTION_UP:
                        if(Math.abs(motionEvent.getY()-DownY)>100){
                            MQTTService.actionCancel(MainActivity.this);
                            Toast.makeText(MainActivity.this,"取消录音",Toast.LENGTH_SHORT).show();
                        }else{
                            MQTTService.actionStop(MainActivity.this);
                            Toast.makeText(MainActivity.this,"完成录音",Toast.LENGTH_SHORT).show();
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }
        });


        player_btn.setTextOff("STOPPED");
        player_btn.setTextOn("PLAYING");
        player_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(player_btn.isChecked()){
                    MQTTService.actionSendMSG(MainActivity.this,"PLAY");
                }else{
                    MQTTService.actionSendMSG(MainActivity.this,"PAUSE");
                }
            }
        });


    }

}

