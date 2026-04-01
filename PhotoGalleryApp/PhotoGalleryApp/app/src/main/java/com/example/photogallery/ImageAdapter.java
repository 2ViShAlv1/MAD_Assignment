package com.example.photogallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * ImageAdapter - RecyclerView Adapter that powers the photo grid in GalleryActivity.
 *
 * Each cell shows a square thumbnail of the image and the file name beneath it.
 * Thumbnails are decoded on a background thread to keep the UI smooth.
 *
 * Design notes:
 *  - Uses WeakReference to the ImageView in the async loader so we do not leak
 *    the view if it is recycled before the decode finishes.
 *  - Implements the ViewHolder pattern for efficient view recycling.
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    // ─────────────────────────────────────────────────────────────────────────
    // Interfaces
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback interface invoked when the user taps an image cell.
     */
    public interface OnImageClickListener {
        void onImageClick(File imageFile);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fields
    // ─────────────────────────────────────────────────────────────────────────

    private final Context context;
    private final List<File> imageFiles;
    private final OnImageClickListener clickListener;

    /** Target thumbnail size in pixels — small enough for fast decoding */
    private static final int THUMB_SIZE = 300;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param context       Activity context used by LayoutInflater.
     * @param imageFiles    The list of image files to display (shared with GalleryActivity).
     * @param clickListener Callback invoked when an image is tapped.
     */
    public ImageAdapter(Context context,
                        List<File> imageFiles,
                        OnImageClickListener clickListener) {
        this.context       = context;
        this.imageFiles    = imageFiles;
        this.clickListener = clickListener;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerView.Adapter overrides
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the grid cell layout for each image
        View itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        File imageFile = imageFiles.get(position);

        // Show file name (without extension) as a caption
        holder.tvImageName.setText(imageFile.getName());

        // Reset the ImageView before loading so stale thumbnails are not shown
        // while the async task runs
        holder.imageView.setImageResource(R.drawable.ic_image_placeholder);

        // Decode and display the thumbnail on a background thread
        new ThumbnailLoader(holder.imageView).execute(imageFile.getAbsolutePath());

        // Wire up the tap handler
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onImageClick(imageFile);
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageFiles.size();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewHolder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ViewHolder caches child views to avoid repeated calls to findViewById.
     */
    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView  tvImageName;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView   = itemView.findViewById(R.id.ivGridImage);
            tvImageName = itemView.findViewById(R.id.tvGridImageName);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Async thumbnail loader
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * ThumbnailLoader decodes an image file into a small Bitmap on a background
     * thread and then updates the target ImageView on the main thread.
     *
     * A WeakReference to the ImageView prevents memory leaks when views are
     * recycled before the background task finishes.
     */
    private static class ThumbnailLoader extends AsyncTask<String, Void, Bitmap> {

        /** Weak reference so we do not prevent GC of recycled views */
        private final WeakReference<ImageView> imageViewRef;

        ThumbnailLoader(ImageView imageView) {
            this.imageViewRef = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            String filePath = params[0];

            // ── Step 1: Read image dimensions only (no pixel data) ──────────
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);

            // ── Step 2: Calculate a sub-sampling factor ──────────────────────
            options.inSampleSize    = calculateInSampleSize(options, THUMB_SIZE, THUMB_SIZE);
            options.inJustDecodeBounds = false;

            // ── Step 3: Decode the down-sampled bitmap ───────────────────────
            return BitmapFactory.decodeFile(filePath, options);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // Only update the view if it still exists and is not yet recycled
            ImageView imageView = imageViewRef.get();
            if (imageView != null && bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }

        /**
         * Calculates an inSampleSize (power of two) that downscales the image
         * to be at least as small as the requested dimensions.
         */
        private int calculateInSampleSize(BitmapFactory.Options options,
                                          int reqWidth, int reqHeight) {
            int height = options.outHeight;
            int width  = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {
                int halfHeight = height / 2;
                int halfWidth  = width  / 2;
                while ((halfHeight / inSampleSize) >= reqHeight
                        && (halfWidth  / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }
            return inSampleSize;
        }
    }
}
