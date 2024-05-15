package com.example.project;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public class PlayerService extends Service {
    private final int NOTIFICATION_ID = 404;
    private MediaSessionCompat mediaSession;
    final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            | PlaybackStateCompat.ACTION_STOP
                            | PlaybackStateCompat.ACTION_PAUSE
                            | PlaybackStateCompat.ACTION_PLAY_PAUSE
                            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                            | PlaybackStateCompat.ACTION_PREPARE
                            );
    final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
    private MusicRepository musicRepository;
    private ExoPlayer exoPlayer;
    private AudioManager audioManager;
    private long currentTimeMillis = 0;
    private boolean isPause = true;
    private boolean isNewTrack = true;

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "my_channel_01";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();

            startForeground(1, notification);
            stopForeground(STOP_FOREGROUND_REMOVE);
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        musicRepository = new MusicRepository(this);

        mediaSession = new MediaSessionCompat(this, "Mp3PlayerService");

        mediaSession.setCallback(mediaSessionCallback);

        Context appContext = getApplicationContext();
        Intent activityIntent = new Intent(appContext, MainActivity.class);
        mediaSession.setSessionActivity(
                PendingIntent.getActivity(appContext, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE));

        exoPlayer = new ExoPlayer.Builder(this).build();

        Intent mediaButtonIntent = new Intent(
                Intent.ACTION_MEDIA_BUTTON, null, appContext, MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(
                PendingIntent.getBroadcast(appContext, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE));

        Intent broadcastIntent = new Intent("com.example.mp3player.LOAD_COMPLETE");
        sendBroadcast(broadcastIntent);

        //startService(new Intent(getApplicationContext(), PlayerService.class));

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaSession.release();
    }

    MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPrepare() {
            MusicRepository.Track track = musicRepository.getCurrent();

            MediaMetadataCompat metadata = metadataBuilder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, track.getBitmap())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.getDuration())
                    .putString(MediaMetadataCompat.METADATA_KEY_WRITER, musicRepository.toJson())
                    .build();
            mediaSession.setMetadata(metadata);

            exoPlayer.setMediaItem(MediaItem.fromUri(track.getUri()));
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Player.Listener.super.onPlaybackStateChanged(playbackState);
                    if (playbackState == 4) {
                        onSkipToNext();
                    }
                }
            });

            mediaSession.setPlaybackState(
                    stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING,
                            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());

            mediaSession.setPlaybackState(
                    stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());

            refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_PAUSED);
        }
        @Override
        public void onPlay() {
            if (!isPause || isNewTrack) {

                MusicRepository.Track track = musicRepository.getCurrent();

                MediaMetadataCompat metadata = metadataBuilder
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, track.getBitmap())
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle())
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist())
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist())
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.getDuration())
                        .build();
                mediaSession.setMetadata(metadata);

                int audioFocusResult = audioManager.requestAudioFocus(
                        audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    return;

                exoPlayer.setMediaItem(MediaItem.fromUri(track.getUri()));

                exoPlayer.prepare();
                isNewTrack = false;
            }
            isPause = false;
            mediaSession.setActive(true);
            exoPlayer.setPlayWhenReady(true);

            mediaSession.setPlaybackState(
                    stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                            currentTimeMillis, 1).build());

            registerReceiver(
                    becomingNoisyReceiver,
                    new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

            refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_PLAYING);


        }
        @Override
        public void onPause() {
            exoPlayer.setPlayWhenReady(false);

            isPause = true;
            PlaybackStateCompat playbackState = mediaSession.getController().getPlaybackState();
            if (playbackState != null && playbackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                currentTimeMillis = playbackState.getPosition();
            }
            mediaSession.setPlaybackState(
                    stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                            currentTimeMillis, 1).build());

            unregisterReceiver(becomingNoisyReceiver);

            refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_PAUSED);
        }
        @Override
        public void onStop() {
            exoPlayer.setPlayWhenReady(false);

            currentTimeMillis = 0;

            mediaSession.setActive(false);
            audioManager.abandonAudioFocus(audioFocusChangeListener);
            mediaSession.setPlaybackState(
                    stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1).build());

            //unregisterReceiver(becomingNoisyReceiver);

            refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_PAUSED);

            stopSelf();
        }
        @Override
        public void onSkipToNext(){
            exoPlayer.setPlayWhenReady(false);
            mediaSession.setActive(false);

            currentTimeMillis = 0;

            MusicRepository.Track track = musicRepository.getNext();

            MediaMetadataCompat metadata = metadataBuilder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, track.getBitmap())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.getDuration())
                    .build();
            mediaSession.setMetadata(metadata);

            mediaSession.setPlaybackState(
                    stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1).build());

            refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT);

            exoPlayer.setMediaItem(MediaItem.fromUri(track.getUri()));
            isNewTrack = true;
            if (isPause){
                mediaSession.setPlaybackState(
                        stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                                currentTimeMillis, 1).build());
            }
            else {
                mediaSession.setActive(true);
                exoPlayer.prepare();
                exoPlayer.setPlayWhenReady(true);
                mediaSession.setPlaybackState(
                        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                                currentTimeMillis, 1).build());
            }
        }
        @Override
        public void onSkipToPrevious(){
            exoPlayer.setPlayWhenReady(false);
            mediaSession.setActive(false);

            currentTimeMillis = 0;

            MusicRepository.Track track = musicRepository.getPrevious();

            MediaMetadataCompat metadata = metadataBuilder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, track.getBitmap())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.getDuration())
                    .build();
            mediaSession.setMetadata(metadata);


            mediaSession.setPlaybackState(
                    stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1).build());

            refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS);

            exoPlayer.setMediaItem(MediaItem.fromUri(track.getUri()));
            isNewTrack = true;
            if (isPause){
                mediaSession.setPlaybackState(
                        stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                                currentTimeMillis, 1).build());
            }
            else {
                mediaSession.setActive(true);
                exoPlayer.prepare();
                exoPlayer.setPlayWhenReady(true);
                mediaSession.setPlaybackState(
                        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                                currentTimeMillis, 1).build());
            }
        }
        @Override
        public void onSkipToQueueItem(long id) {
            exoPlayer.setPlayWhenReady(false);
            mediaSession.setActive(false);

            currentTimeMillis = 0;

            MusicRepository.Track track = musicRepository.getByPosition(id);

            MediaMetadataCompat metadata = metadataBuilder
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, track.getBitmap())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist())
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.getDuration())
                    .build();
            mediaSession.setMetadata(metadata);

            mediaSession.setPlaybackState(
                    stateBuilder.setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1).build());

            refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT);

            exoPlayer.setMediaItem(MediaItem.fromUri(track.getUri()));
            isNewTrack = true;
            if (isPause){
                mediaSession.setPlaybackState(
                        stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                                currentTimeMillis, 1).build());
            }
            else {
                mediaSession.setActive(true);
                exoPlayer.prepare();
                exoPlayer.setPlayWhenReady(true);
                mediaSession.setPlaybackState(
                        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                                currentTimeMillis, 1).build());
            }
        }
        @Override
        public void onSeekTo(long pos) {
            currentTimeMillis = pos;
            exoPlayer.seekTo(pos);
            exoPlayer.prepare();
            isNewTrack = false;
            if (isPause) {
                refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_PAUSED);
                mediaSession.setPlaybackState(
                        stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                                currentTimeMillis, 1).build());
            }
            else {
                refreshNotificationAndForegroundStatus(PlaybackStateCompat.STATE_PLAYING);
                mediaSession.setPlaybackState(
                        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                                currentTimeMillis, 1).build());
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return new PlayerServiceBinder();
    }

    public class PlayerServiceBinder extends Binder {
        public MediaSessionCompat.Token getMediaSessionToken() {
            return mediaSession.getSessionToken();
        }
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);

        return super.onStartCommand(intent, flags, startId);
    }

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                mediaSessionCallback.onPlay();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mediaSessionCallback.onPause();
                break;
            default:
                mediaSessionCallback.onPause();
                break;
        }
    };

    @SuppressLint("ForegroundServiceType")
    void refreshNotificationAndForegroundStatus(int playbackState) {

        switch (playbackState) {
            case PlaybackStateCompat.STATE_CONNECTING:{
                startForeground(NOTIFICATION_ID, getNotification(playbackState));
            }
            case PlaybackStateCompat.STATE_PLAYING:
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS: {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                NotificationManagerCompat.from(PlayerService.this)
                        .notify(NOTIFICATION_ID, getNotification(playbackState));
                //stopForeground(false);
                break;
            }
            default: {
                stopForeground(true);
                break;
            }
        }
    }

    Notification getNotification(int playbackState) {
        NotificationCompat.Builder builder = MediaStyleHelper.from(this, mediaSession);

        builder.addAction(
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_previous, getString(R.string.previous),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                this,
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));

        if (!isPause)
            builder.addAction(
                    new NotificationCompat.Action(
                            android.R.drawable.ic_media_pause, getString(R.string.pause),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    this,
                                    PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        else
            builder.addAction(
                    new NotificationCompat.Action(
                            android.R.drawable.ic_media_play, getString(R.string.play),
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    this,
                                    PlaybackStateCompat.ACTION_PLAY_PAUSE)));

        builder.addAction(
                new NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                this,
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));

        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                this,
                                PlaybackStateCompat.ACTION_STOP))
                .setMediaSession(mediaSession.getSessionToken())

        );

        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setColor(ContextCompat.getColor(this, R.color.black));

        builder.setShowWhen(false);

        builder.setPriority(NotificationCompat.PRIORITY_HIGH);

        builder.setSilent(true);

        return builder.build();
    }

    final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                mediaSessionCallback.onPause();
            }
        }
    };
}

// TODO: (возможно кнопка зацикливания)
// TODO: интерфейс
// TODO: не убивать сервис
