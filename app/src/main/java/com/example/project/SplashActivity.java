package com.example.project;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private BroadcastReceiver loadCompleteReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        Intent serviceIntent = new Intent(this, PlayerService.class);
        startService(serviceIntent);

        loadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Intent i = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(i);
                finish();
            }
        };
        IntentFilter filter = new IntentFilter("com.example.mp3player.LOAD_COMPLETE");
        registerReceiver(loadCompleteReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loadCompleteReceiver != null) {
            unregisterReceiver(loadCompleteReceiver);
        }
    }
}