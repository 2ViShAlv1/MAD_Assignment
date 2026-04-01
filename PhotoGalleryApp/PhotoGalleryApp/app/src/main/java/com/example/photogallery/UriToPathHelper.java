package com.example.photogallery;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;

/**
 * UriToPathHelper - Utility class for converting Android document-tree URIs
 * to actual file-system paths.
 *
 * Android's document picker (ACTION_OPEN_DOCUMENT_TREE) returns opaque
 * content:// URIs.  We need real paths to scan directories with java.io.File.
 *
 * Supported URI authorities:
 *  - com.android.externalstorage.documents  → primary external / SD-card storage
 *  - com.android.providers.downloads.documents → Downloads folder
 *  - com.android.providers.media.documents  → MediaStore (images, video, audio)
 */
public class UriToPathHelper {

    /**
     * Attempts to convert a document-tree URI to an absolute file-system path.
     *
     * @param context The application context (used for content resolution).
     * @param uri     The URI returned by ACTION_OPEN_DOCUMENT_TREE.
     * @return The absolute path string, or {@code null} if conversion fails.
     */
    public static String getPathFromUri(Context context, Uri uri) {

        // On Android 5.0+ (Lollipop), document tree URIs have a specific structure
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                // Extract the document ID from the tree URI
                // e.g. "primary:Pictures/MyFolder"
                String docId = DocumentsContract.getTreeDocumentId(uri);
                String[] split = docId.split(":");

                String type = split[0];              // e.g. "primary" or "sdcard"
                String relativePath = split.length > 1 ? split[1] : "";  // e.g. "Pictures/MyFolder"

                // ── Primary (internal) external storage ──────────────────────
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory()
                            + File.separator + relativePath;
                }

                // ── SD card or secondary storage ─────────────────────────────
                // Try common mount points for secondary storage
                String[] sdcardPaths = {
                        "/storage/" + type,
                        "/mnt/sdcard/" + type,
                        "/mnt/extSdCard/" + type
                };

                for (String sdPath : sdcardPaths) {
                    File sdFile = new File(sdPath);
                    if (sdFile.exists()) {
                        return sdFile.getAbsolutePath()
                                + (relativePath.isEmpty() ? "" : File.separator + relativePath);
                    }
                }

            } catch (Exception e) {
                // URI parsing failed — fall through to return null
                e.printStackTrace();
            }
        }

        // Could not resolve to a path
        return null;
    }
}
