package com.example.project;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

final class MusicRepository {

    private final ArrayList<Track> data;
    private int currentItemIndex = 0;

    MusicRepository(Context context){
        data = new ArrayList<>();

        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sortOrder = MediaStore.Audio.Media.TITLE;
        Cursor cursor = contentResolver.query(uri, null, selection, null, sortOrder);

        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                @SuppressLint("Range") String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                @SuppressLint("Range") String duration = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
                @SuppressLint("Range") String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));

                Bitmap art = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_album_cover);

               MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
                metaRetriever.setDataSource(context, Uri.parse(data));
                byte[] cover = metaRetriever.getEmbeddedPicture();
                if (cover != null) {
                    Bitmap image = BitmapFactory.decodeByteArray(cover, 0, cover.length);
                    if (image != null)
                        art = image;
                }

                this.data.add(new Track(title, artist, art, Uri.parse(data), Long.parseLong(duration)));
            }
            cursor.close();
        }
    }

    Track getNext() {
        if (currentItemIndex == data.size() - 1)
            currentItemIndex = 0;
        else
            currentItemIndex++;
        return getCurrent();
    }

    Track getByPosition(long pos) {
        currentItemIndex = (int) (pos % data.size());
        return getCurrent();
    }

    Track getPrevious() {
        if (currentItemIndex == 0)
            currentItemIndex = data.size() - 1;
        else
            currentItemIndex--;
        return getCurrent();
    }

    Track getCurrent() {
        return data.get(currentItemIndex);
    }

    public String toJson() {
        JSONArray tracksArray = new JSONArray();
        for (Track track : data){
            tracksArray.put(track.toJson());
        }

        return tracksArray.toString();

    }

    static class Track {
        private final String title;
        private final String artist;
        private Bitmap bitmap;
        private final Uri uri;
        private final Long duration;

        Track(String title, String artist, Bitmap bitmap, Uri uri, Long duration) {
            this.title = title;
            this.artist = artist;
            this.bitmap = bitmap;
            this.uri = uri;
            this.duration = duration;
        }

        String getTitle() {
            return title;
        }

        String getArtist() {
            return artist;
        }

        Bitmap getBitmap() {
            return bitmap;
        }

        void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        Uri getUri() {
            return uri;
        }

        Long getDuration() {
            return duration;
        }

        public JSONObject toJson() {
            try {
                JSONObject jsonObject = new JSONObject();

                jsonObject.put("title", this.title);
                jsonObject.put("artist", this.artist);
                jsonObject.put("duration", this.duration);

                return jsonObject;

            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
        public static Track fromJson(JSONObject jsonObject) {
            try {
                String title = jsonObject.getString("title");
                String artist = jsonObject.getString("artist");
                long duration = jsonObject.getLong("duration");

                return new Track(title, artist, null, null, duration);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
