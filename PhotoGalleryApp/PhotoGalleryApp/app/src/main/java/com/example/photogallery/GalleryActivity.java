package com.example.photogallery;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GalleryActivity - Displays all images in a selected folder as a grid.
 */
public class GalleryActivity extends AppCompatActivity {

    public static final String EXTRA_FOLDER_PATH = "extra_folder_path";
    private static final int GRID_COLUMNS = 3;
    private static final String[] IMAGE_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    };

    private RecyclerView recyclerView;
    private ImageAdapter imageAdapter;
    private View emptyMessageLayout;
    private TextView tvFolderPath;

    private File folder;
    private List<File> imageFiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        String folderPath = getIntent().getStringExtra(EXTRA_FOLDER_PATH);
        if (folderPath == null) {
            Toast.makeText(this, "No folder path provided.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            Toast.makeText(this, "Invalid folder.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvFolderPath       = findViewById(R.id.tvGalleryFolderPath);
        emptyMessageLayout = findViewById(R.id.tvEmptyMessage); // Note: this is a LinearLayout in XML
        recyclerView       = findViewById(R.id.recyclerViewImages);

        tvFolderPath.setText(folder.getAbsolutePath());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Photo Gallery");
        }

        recyclerView.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        imageAdapter = new ImageAdapter(this, imageFiles, imageFile -> {
            Intent intent = new Intent(GalleryActivity.this, ImageDetailActivity.class);
            intent.putExtra(ImageDetailActivity.EXTRA_IMAGE_PATH, imageFile.getAbsolutePath());
            startActivity(intent);
        });
        recyclerView.setAdapter(imageAdapter);

        loadImagesFromFolder();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadImagesFromFolder();
    }

    private void loadImagesFromFolder() {
        imageFiles.clear();
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile() && isImageFile(file)) {
                    imageFiles.add(file);
                }
            }
            // Sort images newest-first
            Collections.sort(imageFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        }

        imageAdapter.notifyDataSetChanged();

        if (imageFiles.isEmpty()) {
            emptyMessageLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyMessageLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
