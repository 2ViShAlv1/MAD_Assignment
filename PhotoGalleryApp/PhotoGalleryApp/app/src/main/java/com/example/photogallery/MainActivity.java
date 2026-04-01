package com.example.photogallery;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MainActivity - The main/home screen of the Photo Gallery app.
 *
 * Responsibilities:
 *  1. Request camera and storage permissions at runtime.
 *  2. Let the user capture a photo using the device camera.
 *  3. Save the captured photo to a user-chosen folder on the device.
 *  4. Let the user pick a folder to browse and launch GalleryActivity.
 */
public class MainActivity extends AppCompatActivity {

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    /** Sub-folder name inside Pictures where photos are saved by default */
    private static final String DEFAULT_FOLDER = "PhotoGalleryApp";

    // ─────────────────────────────────────────────────────────────────────────
    // Member variables
    // ─────────────────────────────────────────────────────────────────────────

    /** Absolute path of the photo file being captured (set before launching camera) */
    private String currentPhotoPath;

    /** The folder where the captured photo will be saved */
    private File selectedSaveFolder;

    /** Displays the currently selected save folder path on the UI */
    private TextView tvSelectedFolder;

    // ─────────────────────────────────────────────────────────────────────────
    // Activity Result Launchers (replaces deprecated startActivityForResult)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Launcher for the system camera app.
     * After the user takes a photo, we copy/move it to the selected folder.
     */
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == RESULT_OK) {
                                // Photo was saved to currentPhotoPath by the camera app.
                                handlePhotoCaptured();
                            } else {
                                Toast.makeText(MainActivity.this,
                                        "Photo capture cancelled.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    /**
     * Launcher for the system folder picker (DocumentTree).
     * Lets the user choose where to save photos and where to browse the gallery.
     */
    private final ActivityResultLauncher<Intent> folderPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                                handleFolderSelected(result.getData());
                            }
                        }
                    });

    /**
     * Launcher for requesting multiple permissions at once.
     * Handles CAMERA + READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES.
     */
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        // Check if all requested permissions were granted
                        boolean allGranted = true;
                        for (Boolean granted : permissions.values()) {
                            if (!granted) {
                                allGranted = false;
                                break;
                            }
                        }

                        if (allGranted) {
                            // All permissions granted — proceed with the pending action
                            Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this,
                                    "Permissions are required to use this feature.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the default save folder (Pictures/PhotoGalleryApp)
        selectedSaveFolder = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                DEFAULT_FOLDER);

        // Create the default folder if it does not exist yet
        if (!selectedSaveFolder.exists()) {
            selectedSaveFolder.mkdirs();
        }

        // ── Bind views ──────────────────────────────────────────────────────
        tvSelectedFolder = findViewById(R.id.tvSelectedFolder);
        Button btnTakePhoto     = findViewById(R.id.btnTakePhoto);
        Button btnChooseFolder  = findViewById(R.id.btnChooseFolder);
        Button btnViewGallery   = findViewById(R.id.btnViewGallery);

        // Display the default folder path in the UI
        updateFolderDisplay();

        // ── Button click listeners ───────────────────────────────────────────

        // Take Photo button: checks permissions then opens the camera
        btnTakePhoto.setOnClickListener(v -> checkPermissionsAndTakePhoto());

        // Choose Folder button: opens Android's built-in folder picker
        btnChooseFolder.setOnClickListener(v -> openFolderPicker());

        // View Gallery button: launches GalleryActivity with the selected folder
        btnViewGallery.setOnClickListener(v -> openGallery());

        // Request permissions at startup so the user is prompted early
        requestRequiredPermissions();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permission handling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the list of permissions needed for this device's Android version
     * and requests any that have not been granted yet.
     */
    private void requestRequiredPermissions() {
        List<String> needed = new ArrayList<>();

        // Camera permission is always required
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }

        // Storage permissions differ by API level
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 and below uses READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            // WRITE_EXTERNAL_STORAGE needed only on Android 9 and below
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }
        }

        if (!needed.isEmpty()) {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    /**
     * Checks that both CAMERA and relevant storage permissions are granted,
     * then dispatches the camera intent. Prompts for permissions if missing.
     */
    private void checkPermissionsAndTakePhoto() {
        boolean cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        boolean storageOk;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            storageOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            storageOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }

        if (cameraOk && storageOk) {
            dispatchTakePictureIntent();
        } else {
            // Re-request missing permissions
            requestRequiredPermissions();
            Toast.makeText(this, "Please grant permissions and try again.", Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a temporary image file, builds a content URI via FileProvider,
     * and fires the ACTION_IMAGE_CAPTURE intent so the system camera saves
     * the full-resolution photo directly to our file.
     */
    private void dispatchTakePictureIntent() {
        // Create a unique file name using the current timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String imageFileName = "PHOTO_" + timeStamp;

        try {
            // Create a temporary file in the app's private cache dir
            // (FileProvider will expose it to the camera app securely)
            File storageDir = getCacheDir();
            File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
            currentPhotoPath = imageFile.getAbsolutePath();

            // Convert the File to a content:// URI that the camera can write to
            Uri photoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile);

            // Build and launch the camera intent
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            // Grant temporary write permission to the camera app
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLauncher.launch(takePictureIntent);

        } catch (IOException e) {
            Toast.makeText(this, "Error creating image file: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Called when the camera returns RESULT_OK.
     * Moves the captured photo from the temp cache location into the
     * user-selected save folder.
     */
    private void handlePhotoCaptured() {
        if (currentPhotoPath == null) return;

        File tempFile = new File(currentPhotoPath);
        if (!tempFile.exists()) {
            Toast.makeText(this, "Captured photo file not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build destination file path inside the chosen folder
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        File destFile = new File(selectedSaveFolder, "PHOTO_" + timeStamp + ".jpg");

        try {
            // Move the file securely across filesystem boundaries
            if (moveFile(tempFile, destFile)) {
                Toast.makeText(this,
                        "Photo saved to:\n" + destFile.getAbsolutePath(),
                        Toast.LENGTH_LONG).show();

                // Notify the MediaStore so the photo appears in the system gallery
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScanIntent.setData(Uri.fromFile(destFile));
                sendBroadcast(mediaScanIntent);
            } else {
                Toast.makeText(this, "Failed to save photo to selected folder.",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error saving photo: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Robustly moves a file from source to destination.
     * Tries renameTo first, then fallback to stream copying if rename fails
     * (e.g. across partitions).
     */
    private boolean moveFile(File source, File dest) throws IOException {
        // Try atomic rename
        if (source.renameTo(dest)) {
            return true;
        }

        // Fallback: Copy and delete
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
        return source.delete();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Folder picking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens Android's built-in DocumentTree picker so the user can select
     * any folder on the device for saving photos / browsing the gallery.
     */
    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // Suggest starting in the Pictures directory
        intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures"));
        folderPickerLauncher.launch(intent);
    }

    /**
     * Handles the result of the folder picker.
     * Resolves the selected tree URI to an actual file-system path and
     * stores it as the active folder.
     *
     * @param data The Intent returned by the folder picker containing the URI.
     */
    private void handleFolderSelected(@NonNull Intent data) {
        Uri treeUri = data.getData();
        if (treeUri == null) return;

        // Convert the document tree URI to a real File path
        String path = UriToPathHelper.getPathFromUri(this, treeUri);

        if (path != null) {
            selectedSaveFolder = new File(path);
            // Create the folder if it doesn't already exist
            if (!selectedSaveFolder.exists()) {
                selectedSaveFolder.mkdirs();
            }
            updateFolderDisplay();
            Toast.makeText(this, "Folder selected: " + path, Toast.LENGTH_SHORT).show();
        } else {
            // Fallback: use the URI's last path segment as a readable label
            Toast.makeText(this,
                    "Folder selected (URI-based access).",
                    Toast.LENGTH_SHORT).show();
            // Store URI string for display purposes
            tvSelectedFolder.setText("Selected: " + treeUri.getLastPathSegment());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gallery navigation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Launches GalleryActivity, passing the selected folder path so the
     * gallery can load and display all images found in that folder.
     */
    private void openGallery() {
        if (selectedSaveFolder == null || !selectedSaveFolder.exists()) {
            Toast.makeText(this, "Please select a valid folder first.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, GalleryActivity.class);
        // Pass the folder path as an extra
        intent.putExtra(GalleryActivity.EXTRA_FOLDER_PATH, selectedSaveFolder.getAbsolutePath());
        startActivity(intent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Refreshes the folder-path label shown on the main screen. */
    private void updateFolderDisplay() {
        if (selectedSaveFolder != null) {
            tvSelectedFolder.setText("Save folder: " + selectedSaveFolder.getAbsolutePath());
        }
    }
}
