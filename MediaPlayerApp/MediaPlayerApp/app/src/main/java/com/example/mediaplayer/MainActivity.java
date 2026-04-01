package com.example.mediaplayer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_FILE_PICK = 200;

    // UI Elements
    private Button btnOpenFile, btnOpenUrl, btnPlay, btnPause, btnStop, btnRestart;
    private SeekBar seekBar;
    private TextView tvStatus, tvCurrentTime, tvTotalTime;
    private VideoView videoView;
    private View audioPanel, videoPanel;

    // Media
    private MediaPlayer mediaPlayer;
    private boolean isAudioMode = false;
    private boolean isVideoMode = false;
    private String currentUrl = "";
    private Uri currentFileUri = null;

    private Handler handler = new Handler();
    private Runnable seekBarUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        setupSeekBarUpdater();
        updateButtonStates(false);
    }

    private void initViews() {
        btnOpenFile = findViewById(R.id.btnOpenFile);
        btnOpenUrl  = findViewById(R.id.btnOpenUrl);
        btnPlay     = findViewById(R.id.btnPlay);
        btnPause    = findViewById(R.id.btnPause);
        btnStop     = findViewById(R.id.btnStop);
        btnRestart  = findViewById(R.id.btnRestart);
        seekBar     = findViewById(R.id.seekBar);
        tvStatus    = findViewById(R.id.tvStatus);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime   = findViewById(R.id.tvTotalTime);
        videoView   = findViewById(R.id.videoView);
        audioPanel  = findViewById(R.id.audioPanel);
        videoPanel  = findViewById(R.id.videoPanel);
    }

    private void setupListeners() {
        btnOpenFile.setOnClickListener(v -> openFilePicker());
        btnOpenUrl.setOnClickListener(v -> showUrlDialog());

        btnPlay.setOnClickListener(v -> playMedia());
        btnPause.setOnClickListener(v -> pauseMedia());
        btnStop.setOnClickListener(v -> stopMedia());
        btnRestart.setOnClickListener(v -> restartMedia());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (isAudioMode && mediaPlayer != null) {
                        mediaPlayer.seekTo(progress);
                        updateTimeLabels(progress, mediaPlayer.getDuration());
                    } else if (isVideoMode) {
                        videoView.seekTo(progress);
                        updateTimeLabels(progress, videoView.getDuration());
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupSeekBarUpdater() {
        seekBarUpdater = new Runnable() {
            @Override
            public void run() {
                try {
                    if (isAudioMode && mediaPlayer != null && mediaPlayer.isPlaying()) {
                        int current = mediaPlayer.getCurrentPosition();
                        seekBar.setProgress(current);
                        updateTimeLabels(current, mediaPlayer.getDuration());
                    } else if (isVideoMode && videoView.isPlaying()) {
                        int current = videoView.getCurrentPosition();
                        seekBar.setProgress(current);
                        updateTimeLabels(current, videoView.getDuration());
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 500);
            }
        };
    }

    // ─── Open File ───────────────────────────────────────────────────────────

    private void openFilePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: READ_MEDIA_AUDIO
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_PERMISSION);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
                return;
            }
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Audio File"), REQUEST_FILE_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_PICK && resultCode == RESULT_OK && data != null) {
            currentFileUri = data.getData();
            currentUrl = "";
            isAudioMode = true;
            isVideoMode = false;
            showAudioPanel();
            prepareAudioFromUri(currentFileUri);
        }
    }

    // ─── Open URL ────────────────────────────────────────────────────────────

    private void showUrlDialog() {
        final EditText input = new EditText(this);
        input.setHint("https://example.com/video.mp4");
        if (!currentUrl.isEmpty()) input.setText(currentUrl);

        new AlertDialog.Builder(this)
                .setTitle("Enter Stream URL")
                .setMessage("Enter a video/audio stream URL:")
                .setView(input)
                .setPositiveButton("Load", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        currentUrl = url;
                        currentFileUri = null;
                        isVideoMode = true;
                        isAudioMode = false;
                        showVideoPanel();
                        prepareVideoFromUrl(url);
                    } else {
                        Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Prepare Audio ───────────────────────────────────────────────────────

    private void prepareAudioFromUri(Uri uri) {
        releaseMediaPlayer();
        setStatus("Loading audio...");
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(getApplicationContext(), uri);
            mediaPlayer.setOnPreparedListener(mp -> {
                setStatus("Audio ready ▶ Tap Play");
                seekBar.setMax(mp.getDuration());
                updateTimeLabels(0, mp.getDuration());
                updateButtonStates(true);
                handler.post(seekBarUpdater);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                setStatus("Playback complete");
                seekBar.setProgress(0);
                updateButtonStates(true);
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                setStatus("Error playing audio");
                Toast.makeText(this, "Error: " + what, Toast.LENGTH_SHORT).show();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            setStatus("Failed to load audio");
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─── Prepare Video ───────────────────────────────────────────────────────

    private void prepareVideoFromUrl(String url) {
        stopMedia();
        setStatus("Loading video stream...");
        videoView.setVideoURI(Uri.parse(url));
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            seekBar.setMax(videoView.getDuration());
            updateTimeLabels(0, videoView.getDuration());
            setStatus("Video ready ▶ Tap Play");
            updateButtonStates(true);
            handler.post(seekBarUpdater);
        });
        videoView.setOnCompletionListener(mp -> {
            setStatus("Playback complete");
            seekBar.setProgress(0);
            updateButtonStates(true);
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            setStatus("Error streaming video");
            Toast.makeText(this, "Stream error. Check the URL.", Toast.LENGTH_LONG).show();
            return true;
        });
    }

    // ─── Controls ────────────────────────────────────────────────────────────

    private void playMedia() {
        if (isAudioMode && mediaPlayer != null) {
            mediaPlayer.start();
            setStatus("Playing audio...");
        } else if (isVideoMode) {
            videoView.start();
            setStatus("Streaming video...");
        }
    }

    private void pauseMedia() {
        if (isAudioMode && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            setStatus("Paused");
        } else if (isVideoMode && videoView.isPlaying()) {
            videoView.pause();
            setStatus("Paused");
        }
    }

    private void stopMedia() {
        if (isAudioMode && mediaPlayer != null) {
            mediaPlayer.stop();
            try { mediaPlayer.prepare(); } catch (Exception ignored) {}
            mediaPlayer.seekTo(0);
            seekBar.setProgress(0);
            setStatus("Stopped");
        } else if (isVideoMode) {
            videoView.stopPlayback();
            seekBar.setProgress(0);
            setStatus("Stopped");
            if (!currentUrl.isEmpty()) prepareVideoFromUrl(currentUrl);
        }
    }

    private void restartMedia() {
        if (isAudioMode && mediaPlayer != null) {
            mediaPlayer.seekTo(0);
            mediaPlayer.start();
            setStatus("Restarted ▶");
        } else if (isVideoMode) {
            videoView.seekTo(0);
            videoView.start();
            setStatus("Restarted ▶");
        }
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    private void showAudioPanel() {
        audioPanel.setVisibility(View.VISIBLE);
        videoPanel.setVisibility(View.GONE);
    }

    private void showVideoPanel() {
        audioPanel.setVisibility(View.GONE);
        videoPanel.setVisibility(View.VISIBLE);
    }

    private void setStatus(String msg) {
        tvStatus.setText(msg);
    }

    private void updateButtonStates(boolean mediaLoaded) {
        btnPlay.setEnabled(mediaLoaded);
        btnPause.setEnabled(mediaLoaded);
        btnStop.setEnabled(mediaLoaded);
        btnRestart.setEnabled(mediaLoaded);
        seekBar.setEnabled(mediaLoaded);
    }

    private void updateTimeLabels(int currentMs, int totalMs) {
        tvCurrentTime.setText(formatTime(currentMs));
        tvTotalTime.setText(formatTime(totalMs));
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ─── Permissions ─────────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this, "Storage permission is required to open files.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(seekBarUpdater);
        releaseMediaPlayer();
    }
}
