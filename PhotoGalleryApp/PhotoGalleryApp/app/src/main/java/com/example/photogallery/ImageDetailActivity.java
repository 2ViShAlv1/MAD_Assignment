package com.example.photogallery;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ImageDetailActivity - Shows the full-size image along with its metadata.
 *
 * Responsibilities:
 *  1. Display the selected image in full view.
 *  2. Show image metadata: name, path, file size, and date taken/modified.
 *  3. Provide a Delete button with a confirmation dialog.
 *  4. After successful deletion, navigate back to GalleryActivity.
 */
public class ImageDetailActivity extends AppCompatActivity {

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    /** Key for receiving the image file path from GalleryActivity */
    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    // ─────────────────────────────────────────────────────────────────────────
    // Member variables
    // ─────────────────────────────────────────────────────────────────────────

    /** The image file being viewed */
    private File imageFile;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_detail);

        // ── Get image path from Intent ───────────────────────────────────────
        String imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (imagePath == null) {
            Toast.makeText(this, "Image path not provided.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Toast.makeText(this, "Image file not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Enable back navigation in ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Image Details");
        }

        // ── Bind views ───────────────────────────────────────────────────────
        ImageView imageView   = findViewById(R.id.ivDetailImage);
        TextView  tvName      = findViewById(R.id.tvImageName);
        TextView  tvPath      = findViewById(R.id.tvImagePath);
        TextView  tvSize      = findViewById(R.id.tvImageSize);
        TextView  tvDate      = findViewById(R.id.tvImageDate);
        Button    btnDelete   = findViewById(R.id.btnDeleteImage);

        // ── Display the image ────────────────────────────────────────────────
        loadImageIntoView(imageView);

        // ── Populate metadata fields ─────────────────────────────────────────
        tvName.setText("Name: " + imageFile.getName());
        tvPath.setText("Path: " + imageFile.getAbsolutePath());
        tvSize.setText("Size: " + formatFileSize(imageFile.length()));
        tvDate.setText("Date Taken: " + formatDate(imageFile.lastModified()));

        // ── Delete button ────────────────────────────────────────────────────
        btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Decodes the image file into a Bitmap with downsampling to avoid
     * OutOfMemoryErrors for large photos, then sets it on the ImageView.
     *
     * @param imageView The ImageView to load the bitmap into.
     */
    private void loadImageIntoView(ImageView imageView) {
        // Step 1: Decode only the image dimensions (no pixels in memory yet)
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), boundsOptions);

        // Step 2: Calculate an appropriate inSampleSize to fit a ~1024px target
        boundsOptions.inSampleSize = calculateInSampleSize(boundsOptions, 1024, 1024);
        boundsOptions.inJustDecodeBounds = false;

        // Step 3: Decode the actual (down-sampled) bitmap
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), boundsOptions);

        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            Toast.makeText(this, "Could not load image.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Calculates the largest power-of-two inSampleSize that keeps the decoded
     * image at or above the requested dimensions.
     *
     * @param options   Options object already populated with raw image dimensions.
     * @param reqWidth  Target width in pixels.
     * @param reqHeight Target height in pixels.
     * @return Suitable inSampleSize value (always a power of two).
     */
    private int calculateInSampleSize(BitmapFactory.Options options,
                                      int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width  = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth  = width / 2;

            // Keep halving until both dimensions are smaller than the target
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delete logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows an AlertDialog asking the user to confirm the deletion.
     * Only proceeds with deletion if the user taps "Delete".
     */
    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Image")
                .setMessage("Are you sure you want to permanently delete\n\""
                        + imageFile.getName() + "\"?")
                // Positive (destructive) action
                .setPositiveButton("Delete", (dialog, which) -> deleteImage())
                // Negative action — does nothing, just dismisses the dialog
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Deletes the image file from the device storage.
     * On success:
     *  - Broadcasts a media-scan intent so the system gallery is updated.
     *  - Navigates back to GalleryActivity (finishing this activity).
     * On failure:
     *  - Shows an error Toast.
     */
    private void deleteImage() {
        if (imageFile.delete()) {
            // Tell the MediaStore about the deletion
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(Uri.fromFile(imageFile));
            sendBroadcast(mediaScanIntent);

            Toast.makeText(this, "Image deleted successfully.", Toast.LENGTH_SHORT).show();

            // Go back to the gallery; GalleryActivity will refresh in onResume()
            finish();
        } else {
            Toast.makeText(this, "Failed to delete image. Please try again.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Formatting helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a file size in bytes to a human-readable string (B, KB, MB, GB).
     *
     * @param sizeBytes File size in bytes.
     * @return Formatted string, e.g. "2.34 MB".
     */
    private String formatFileSize(long sizeBytes) {
        if (sizeBytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = (int) (Math.log10(sizeBytes) / Math.log10(1024));
        unitIndex = Math.min(unitIndex, units.length - 1);
        double value = sizeBytes / Math.pow(1024, unitIndex);
        return new DecimalFormat("#,##0.##").format(value) + " " + units[unitIndex];
    }

    /**
     * Converts a timestamp (milliseconds since epoch) to a readable date string.
     *
     * @param timeMillis Milliseconds since epoch (from File.lastModified()).
     * @return Formatted date/time string, e.g. "15 Jan 2025, 14:30:05".
     */
    private String formatDate(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeMillis));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ActionBar navigation
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
