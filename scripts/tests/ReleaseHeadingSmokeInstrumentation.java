package com.mlevngr.inknote;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Platform-only driver: deliberately has no dependency on unminified application classes. */
public final class ReleaseHeadingSmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        Activity editor = null;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = getContext().getAssets().open("skin.json")) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
            JSONArray headings = new JSONObject(json).getJSONObject("light").getJSONArray("headings");
            final int[] colors = new int[6];
            for (int i = 0; i < colors.length; i++) colors[i] = Color.parseColor(headings.getString(i));
            getTargetContext().getSharedPreferences("skin", 0).edit()
                .putString("json", json).putBoolean("enabled", true).commit();

            String name = "Release heading check " + UUID.randomUUID();
            File note = new File(getTargetContext().getFilesDir(), "notes/" + name + ".note");
            if (!new File(note, "assets").mkdirs()) throw new IllegalStateException("Cannot create fixture");
            StringBuilder markdown = new StringBuilder();
            for (int i = 1; i <= 6; i++) {
                for (int n = 0; n < i; n++) markdown.append('#');
                markdown.append(" Release heading ").append(i).append('\n');
            }
            try (FileOutputStream file = new FileOutputStream(new File(note, "note.md"))) {
                file.write(markdown.toString().getBytes(StandardCharsets.UTF_8));
            }
            new File(note, ".body-title-separated-v1").createNewFile();
            Intent intent = new Intent().setClassName("com.mlevngr.inknote", "com.mlevngr.inknote.EditorActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("note_name", name)
                .putStringArrayListExtra("folder_names", new ArrayList<String>());
            editor = startActivitySync(intent);
            final Activity activity = editor;
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (find(activity.getWindow().getDecorView(), "Release heading 6") == null) {
                if (SystemClock.uptimeMillis() >= deadline) throw new IllegalStateException("Editor did not render headings");
                SystemClock.sleep(50);
            }
            waitForIdleSync();
            SystemClock.sleep(250);
            final Throwable[] renderingError = new Throwable[1];
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    try {
                        for (int i = 0; i < 6; i++) verify(find(activity.getWindow().getDecorView(), "Release heading " + (i + 1)), colors[i], i + 1);
                    } catch (Throwable error) { renderingError[0] = error; }
                }
            });
            if (renderingError[0] != null) throw new IllegalStateException("Release heading rendering failed", renderingError[0]);
            result.putInt("failures", 0);
            result.putString("stream", "Release editor: all six heading spans and rendered colors passed\n");
        } catch (Throwable error) {
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            result.putInt("failures", 1);
            result.putString("stream", trace.toString());
        } finally {
            if (editor != null) {
                Bitmap screenshot = getUiAutomation().takeScreenshot();
                try (FileOutputStream file = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null), "release-headings.png"))) {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, file);
                } catch (Exception ignored) { }
                screenshot.recycle();
                final Activity activity = editor;
                runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); } });
            }
        }
        finish(result.getInt("failures") == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private static TextView find(View root, String text) {
        if (root instanceof TextView && ((TextView) root).getText().toString().equals(text)) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void verify(TextView view, int expected, int level) {
        if (view == null || !(view.getText() instanceof Spanned)) throw new IllegalStateException("H" + level + " is not styled");
        Spanned text = (Spanned) view.getText();
        boolean spanFound = false;
        for (ForegroundColorSpan span : text.getSpans(0, text.length(), ForegroundColorSpan.class)) {
            if (span.getForegroundColor() == expected) spanFound = true;
        }
        if (!spanFound) throw new IllegalStateException("Release H" + level + " has no expected color span");
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        boolean drawn = false;
        for (int pixel : pixels) if (pixel == expected) drawn = true;
        bitmap.recycle();
        if (!drawn) throw new IllegalStateException("Release H" + level + " paints without its skin color");
    }
}
