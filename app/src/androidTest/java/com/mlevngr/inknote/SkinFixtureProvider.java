package com.mlevngr.inknote;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** A platform-only fixture, since this test provider runs outside the instrumented app process. */
public final class SkinFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!"seed".equals(method) || arg == null) throw new IllegalArgumentException();
        try {
            write("valid", arg);
            write("invalid", "{broken}");
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        return Bundle.EMPTY;
    }

    private void write(String name, String content) throws IOException {
        try (FileOutputStream output = new FileOutputStream(new File(getContext().getCacheDir(), name))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!"valid".equals(name) && !"invalid".equals(name) && !"export".equals(name)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), name),
            mode.contains("w") ? ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                               : ParcelFileDescriptor.MODE_READ_ONLY);
    }
}
