package com.example.munchai.backend.media;

import android.net.Uri;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MainThread;
import androidx.appcompat.app.AppCompatActivity;

public final class PhotoCaptureManager {

    public interface Callbacks {
        void onPhotoReady(Uri uri);

        void onCaptureCanceled();
    }

    private final AppCompatActivity activity;
    private final ImageView preview;
    private final PhotoStore photoStore;
    private final Callbacks callbacks;

    private ActivityResultLauncher<Uri> launcher;
    private PhotoStore.Capture current;

    public PhotoCaptureManager(AppCompatActivity activity,
                               ImageView preview,
                               PhotoStore photoStore,
                               Callbacks callbacks) {
        this.activity = activity;
        this.preview = preview;
        this.photoStore = photoStore;
        this.callbacks = callbacks;
    }
    @MainThread
    public void register() {
        launcher = activity.registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                result -> {
                    if (Boolean.TRUE.equals(result)) {
                        if (preview != null && current != null) {
                            preview.setImageURI(current.uri);
                        }
                        if (callbacks != null && current != null) {
                            callbacks.onPhotoReady(current.uri);
                        }
                    } else {
                        cleanupCurrent();
                        if (callbacks != null) callbacks.onCaptureCanceled();
                    }
                }
        );
    }
    @MainThread
    public void startCapture() {
        try {
            // replace any previous temp file
            cleanupCurrent();
            current = photoStore.newCapture();
            launcher.launch(current.uri);
        } catch (Exception e) {
            Toast.makeText(activity, "Unable to start camera", Toast.LENGTH_SHORT).show();
            if (callbacks != null) callbacks.onCaptureCanceled();
        }
    }
    @MainThread
    public void retake() {
        startCapture();
    }
    public boolean hasPhoto() {
        return current != null && current.uri != null;
    }
    public Uri getCurrentUri() {
        return current != null ? current.uri : null;
    }
    public void cleanupCurrent() {
        if (current != null) {
            photoStore.deleteSilently(current.file);
            photoStore.revokeUriPermissions(current.uri);
            current = null;
        }
    }
}
