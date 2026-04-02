package com.example.audiovideoapp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private TextView textViewPath;
    private VideoView videoView;
    private MediaPlayer mediaPlayer;
    private boolean isAudioSelected = false;
    private boolean isVideoSelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textViewPath = findViewById(R.id.textViewPath);
        videoView = findViewById(R.id.videoView);

        MaterialButton btnOpenFile = findViewById(R.id.btnOpenFile);
        MaterialButton btnOpenURL = findViewById(R.id.btnOpenURL);
        MaterialButton btnPlay = findViewById(R.id.btnPlay);
        MaterialButton btnPause = findViewById(R.id.btnPause);
        MaterialButton btnStop = findViewById(R.id.btnStop);
        MaterialButton btnRestart = findViewById(R.id.btnRestart);

        btnOpenFile.setOnClickListener(v -> openFile());
        btnOpenURL.setOnClickListener(v -> openURL());
        btnPlay.setOnClickListener(v -> play());
        btnPause.setOnClickListener(v -> pause());
        btnStop.setOnClickListener(v -> stop());
        btnRestart.setOnClickListener(v -> restart());
    }

    private void openFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"audio/*", "video/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, "Select Audio or Video File"), 1);
    }

    private void openURL() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Media URL");

        final EditText input = new EditText(this);
        input.setHint("https://www.example.com/video.mp4");
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                loadMedia(Uri.parse(url), url.toLowerCase().contains("mp4") || url.toLowerCase().contains("mkv"));
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void loadMedia(Uri uri, boolean isVideo) {
        stopMedia();
        releaseMediaPlayer();
        
        textViewPath.setText(uri.toString());
        
        if (isVideo) {
            isVideoSelected = true;
            isAudioSelected = false;
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(uri);
            videoView.setOnPreparedListener(mp -> videoView.start());
        } else {
            isVideoSelected = false;
            isAudioSelected = true;
            videoView.setVisibility(View.GONE);
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(this, uri);
                mediaPlayer.prepareAsync();
                mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            } catch (IOException e) {
                Toast.makeText(this, "Error loading audio", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private void play() {
        if (isAudioSelected && mediaPlayer != null) {
            mediaPlayer.start();
        } else if (isVideoSelected) {
            videoView.start();
        }
    }

    private void pause() {
        if (isAudioSelected && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else if (isVideoSelected && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    private void stopMedia() {
        if (isAudioSelected && mediaPlayer != null) {
            mediaPlayer.stop();
            try {
                mediaPlayer.prepare();
                mediaPlayer.seekTo(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (isVideoSelected) {
            videoView.stopPlayback();
        }
    }

    private void stop() {
        stopMedia();
        if (isVideoSelected) {
            // Reload to reset video state
            videoView.setVideoURI(Uri.parse(textViewPath.getText().toString()));
        }
    }

    private void restart() {
        if (isAudioSelected && mediaPlayer != null) {
            mediaPlayer.seekTo(0);
            mediaPlayer.start();
        } else if (isVideoSelected) {
            videoView.seekTo(0);
            videoView.start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String type = getContentResolver().getType(uri);
                boolean isVideo = type != null && type.startsWith("video");
                loadMedia(uri, isVideo);
            }
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
    }
}
