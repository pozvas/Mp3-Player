package com.example.project;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class TrackListAdapter extends BaseAdapter {

    private Context context;
    private ArrayList<MusicRepository.Track> tracks;
    private int selectedColor = Color.BLACK;
    private TextView textView;

    public TrackListAdapter(Context context, ArrayList<MusicRepository.Track> tracks) {
        this.context = context;
        this.tracks = tracks;
    }

    @Override
    public int getCount() {
        return tracks.size();
    }

    @Override
    public Object getItem(int position) {
        return tracks.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_item, parent, false);
        }

        textView = convertView.findViewById(R.id.textView);
        textView.setText("Элемент " + tracks.get(position).getTitle());
        convertView.setBackgroundColor(position % 2 == 0 ? 0xFFE0E0E0 : 0xFFFFFFFF);
        return convertView;
    }
}

