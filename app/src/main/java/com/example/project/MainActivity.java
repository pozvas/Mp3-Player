package com.example.project;


import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

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
    private ImageView nextButton;
    private ImageView previousButton;
    private SeekBar seekBar;
    private TextView played;
    private boolean isSeeking = false;
    private TrackListAdapter adapter;
    private ListView trackList;
    private DrawerLayout drawerLayout;
    private boolean isPlaying = false;
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
                                played.setText("00:00");
                                seekBar.setProgress(0);
                                ChangeTrackData();
                            } else if(state.getState() == PlaybackStateCompat.STATE_CONNECTING){
                                SetTracks();
                            } else {
                                isPlaying =
                                        state.getState() == PlaybackStateCompat.STATE_PLAYING;
                                if (isPlaying){
                                    customHandler.postDelayed(updatePlayedThread, 0);
                                    playButton.setImageResource(R.drawable.ic_pause);
                                }
                                else {
                                    customHandler.removeCallbacks(updatePlayedThread);
                                    playButton.setImageResource(R.drawable.ic_play);
                                }
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

    private void SetTracks() {
        adapter = new TrackListAdapter(this, fromJson((String) mediaController.getMetadata().getText(MediaMetadataCompat.METADATA_KEY_WRITER)));
        trackList.setAdapter(adapter);
        trackList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mediaController.getTransportControls().skipToQueueItem(position);
                drawerLayout.close();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        playButton = findViewById(R.id.btn_play);
        nextButton = findViewById(R.id.btn_next);
        previousButton = findViewById(R.id.btn_prev);
        seekBar = findViewById(R.id.seek_bar);
        played = findViewById(R.id.song_played);

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        ImageButton test = findViewById(R.id.showAllSongs);
        test.setOnClickListener((v -> {
            drawerLayout.open();
        }));
        trackList = findViewById(R.id.track_list);


        //startService(new Intent(this, PlayerService.class));
        bindService(new Intent(this, PlayerService.class), serviceConnection, BIND_AUTO_CREATE);

        playButton.setOnClickListener((v -> {
            if(!isPlaying) {
                if (mediaController != null)
                    mediaController.getTransportControls().play();
                isPlaying = true;
            } else {
                if (mediaController != null)
                    mediaController.getTransportControls().pause();
                isPlaying = false;
            }
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

    private ArrayList<MusicRepository.Track> fromJson(String json) {
        ArrayList<MusicRepository.Track> tracks = new ArrayList<>();
        JSONArray jsonArray = null;
        try {
            jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                MusicRepository.Track person = MusicRepository.Track.fromJson(jsonObject);
                tracks.add(person);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return tracks;

    }

}
