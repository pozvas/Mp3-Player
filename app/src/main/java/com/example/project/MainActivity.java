package com.example.project;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;

public class MainActivity extends AppCompatActivity {
    PlayerService.PlayerServiceBinder playerServiceBinder;
    MediaControllerCompat mediaController;
    private final Handler customHandler = new Handler();
    private final Runnable updatePlayedThread = new Runnable() {
        @Override
        public void run() {
            ChangePlayed();
            customHandler.postDelayed(this, 1000);
        }
    };
    private ImageView playButton;
    private ImageView pauseButton;
    private ImageView stopButton;
    private ImageView nextButton;
    private ImageView previousButton;
    private SeekBar seekBar;
    private TextView played;
    private boolean isSeeking = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            playerServiceBinder = (PlayerService.PlayerServiceBinder) service;
            mediaController = new MediaControllerCompat(
                    MainActivity.this, playerServiceBinder.getMediaSessionToken());
            mediaController.registerCallback(
                    new MediaControllerCompat.Callback() {
                        @Override
                        public void onPlaybackStateChanged(PlaybackStateCompat state) {
                            if (state == null)
                                return;
                            if (state.getState() == PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS ||
                                    state.getState() == PlaybackStateCompat.STATE_SKIPPING_TO_NEXT) {
                                ChangeTrackData();
                            } else {
                                boolean playing =
                                        state.getState() == PlaybackStateCompat.STATE_PLAYING;
                                if (playing){
                                    customHandler.postDelayed(updatePlayedThread, 0);
                                }
                                else {
                                    customHandler.removeCallbacks(updatePlayedThread);
                                }
                                playButton.setEnabled(!playing);
                                pauseButton.setEnabled(playing);
                                stopButton.setEnabled(playing);
                            }
                        }
                    }
            );
            mediaController.getTransportControls().prepare();

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            playerServiceBinder = null;
            mediaController = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, 1);
            }
        }
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
            return;
        }

        playButton = findViewById(R.id.btn_play);
        pauseButton = findViewById(R.id.btn_pause);
        stopButton = findViewById(R.id.btn_stop);
        nextButton = findViewById(R.id.btn_next);
        previousButton = findViewById(R.id.btn_prev);
        seekBar = findViewById(R.id.seek_bar);
        played = findViewById(R.id.song_played);


        startService(new Intent(this, PlayerService.class));
        bindService(new Intent(this, PlayerService.class), serviceConnection, BIND_AUTO_CREATE);


        playButton.setOnClickListener((v -> {
            if (mediaController != null)
                mediaController.getTransportControls().play();
        }));

        pauseButton.setOnClickListener((v -> {
            if (mediaController != null)
                mediaController.getTransportControls().pause();
        }));

        stopButton.setOnClickListener((v -> {
            if (mediaController != null)
                mediaController.getTransportControls().stop();
        }));

        nextButton.setOnClickListener((v -> {
            if (mediaController != null)
                mediaController.getTransportControls().skipToNext();
        }));

        previousButton.setOnClickListener((v -> {
            if (mediaController != null)
                mediaController.getTransportControls().skipToPrevious();
        }));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int progress = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    this.progress = progress;
                    played.setText(MillisToString(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeeking = false;
                if (mediaController != null)
                    mediaController.getTransportControls().seekTo(progress);
            }
        });
    }

    private void ChangeTrackData() {
        MediaMetadataCompat data = mediaController.getMetadata();

        TextView title = findViewById(R.id.song_title);
        TextView artist = findViewById(R.id.artist_name);
        TextView duration = findViewById(R.id.song_duration);
        ImageView image = findViewById(R.id.album_cover);

        seekBar.setMax((int) data.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
        title.setText(data.getText(MediaMetadataCompat.METADATA_KEY_TITLE));
        artist.setText(data.getText(MediaMetadataCompat.METADATA_KEY_ARTIST));
        image.setImageBitmap(data.getBitmap(MediaMetadataCompat.METADATA_KEY_ART));
        duration.setText(MillisToString(data.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)));
    }
    private void ChangePlayed() {
        if(!isSeeking) {
            seekBar.setProgress((int) mediaController.getPlaybackState().getPosition());
            if (mediaController.getMetadata() != null)
                played.setText(MillisToString(mediaController.getPlaybackState().getPosition()));
            else
                played.setText("00:00");
        }
    }

    private String MillisToString(long millis) {
        long secs = millis / 1000;
        long mins = secs / 60;
        secs = secs % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
    }

}
