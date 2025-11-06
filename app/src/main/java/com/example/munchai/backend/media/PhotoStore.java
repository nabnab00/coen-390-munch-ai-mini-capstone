package com.example.munchai.backend.media;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public final class PhotoStore {

    private final Context context;
    private final String authority;

    public PhotoStore(Context context) {
        this.context = context.getApplicationContext();
        this.authority = this.context.getPackageName() + ".fileprovider";
    }
    public Capture newCapture() throws IOException {
        File dir = new File(context.getCacheDir(), "images");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create cache/images directory");
        }
        File file = File.createTempFile("meal_", ".jpg", dir);
        Uri uri = FileProvider.getUriForFile(context, authority, file);
        return new Capture(uri, file);
    }

    public void revokeUriPermissions(Uri uri) {
        if (uri == null) return;
        context.revokeUriPermission(
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION
        );
    }
    //keep this for now just in case it crashes from like constantly taking photos remove it after once we store photos better
    public void deleteSilently(File f) {
        if (f != null) {
            try {
                f.delete();
            } catch (Throwable ignored) {}
        }
    }

    public static final class Capture {
        public final Uri uri;
        public final File file;
        public Capture(Uri uri, File file) {
            this.uri = uri;
            this.file = file;
        }
    }
}
