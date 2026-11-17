/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.hushgram.extension.shared.L10n;
import app.hushgram.extension.shared.Logger;
import app.hushgram.extension.shared.Utils;

/**
 * Manages exporting settings to and importing settings from standard storage (Download/HushGram),
 * system document pickers, and clipboard.
 */
public final class SettingsBackupHelper {
    public static final String FOLDER = "InstagramPlus";
    public static final String LEGACY_FOLDER = "HushGram";
    public static final String RELATIVE_FOLDER = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER;

    private SettingsBackupHelper() {}

    public static final class ExportResult {
        public final String fileName;
        public final String displayPath;
        public final Uri uri;
        public final byte[] data;

        public ExportResult(String fileName, String displayPath, Uri uri, byte[] data) {
            this.fileName = fileName;
            this.displayPath = displayPath;
            this.uri = uri;
            this.data = data;
        }
    }

    public static final class BackupEntry {
        public final String name;
        public final Uri uri;
        public final long modified;
        public final long size;
        public final String formattedDate;
        public final String formattedSize;

        public BackupEntry(String name, Uri uri, long modified, long size) {
            this.name = name;
            this.uri = uri;
            this.modified = modified;
            this.size = size;
            if (modified > 0) {
                this.formattedDate = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                        .format(new Date(modified));
            } else {
                this.formattedDate = "";
            }
            this.formattedSize = formatSize(size);
        }
    }

    /**
     * Converts a raw file size in bytes to a human-readable string (e.g. 14.2 KB).
     */
    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Exports current settings to Download/HushGram/ with optional timestamp in the filename.
     */
    public static ExportResult exportToDownloads(Context context) throws IOException {
        String fileName = ConfigurationBackup.fileName(new Date(), Settings.DATE_SETTINGS_EXPORT.savedValue());
        byte[] bytes = ConfigurationBackup.export();

        Uri uri = null;
        String displayPath;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            // If replacing HushGram-settings.json (dated=false), remove older file with same name
            try {
                String sel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND "
                        + MediaStore.MediaColumns.DISPLAY_NAME + " = ?";
                String[] selArgs = new String[]{"%" + FOLDER + "%", fileName};
                resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, sel, selArgs);
            } catch (Throwable ignored) {}

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_FOLDER);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore could not create file in " + RELATIVE_FOLDER);
            }
            try {
                try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("Could not open stream for " + fileName);
                    out.write(bytes);
                }
                ContentValues finish = new ContentValues();
                finish.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, finish, null, null);
                displayPath = "Download/" + FOLDER + "/" + fileName;
            } catch (Exception ex) {
                try {
                    resolver.delete(uri, null, null);
                } catch (Throwable ignored) {}
                throw ex;
            }
        } else {
            // Android 9 and below
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
            if (!dir.exists() && !dir.mkdirs()) {
                dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FOLDER);
                if (!dir.exists() && !dir.mkdirs()) {
                    dir = context.getFilesDir();
                }
            }
            File file = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            uri = Uri.fromFile(file);
            displayPath = dir.getAbsolutePath() + "/" + fileName;
        }

        return new ExportResult(fileName, displayPath, uri, bytes);
    }

    /**
     * Writes backup bytes to a user-chosen custom document URI.
     */
    public static void exportToCustomUri(Context context, Uri uri, byte[] bytes) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Could not open destination stream");
            out.write(bytes);
        }
    }

    /**
     * Finds existing backup JSON files in Download/HushGram and app storage.
     */
    public static List<BackupEntry> findBackups(Context context) {
        List<BackupEntry> list = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        // 1. Query MediaStore on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentResolver resolver = context.getContentResolver();
                String[] projection = new String[]{
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DATE_MODIFIED,
                        MediaStore.MediaColumns.SIZE
                };
                String selection = "(" + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR "
                        + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR "
                        + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? OR "
                        + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?) AND "
                        + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?";
                String[] selectionArgs = new String[]{
                        "%Download/" + FOLDER + "%", "%Downloads/" + FOLDER + "%",
                        "%Download/" + LEGACY_FOLDER + "%", "%Downloads/" + LEGACY_FOLDER + "%",
                        "%.json"
                };
                String sortOrder = MediaStore.MediaColumns.DATE_MODIFIED + " DESC";

                try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        projection, selection, selectionArgs, sortOrder)) {
                    if (cursor != null) {
                        int idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
                        int nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                        int dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
                        int sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);

                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idCol);
                            String name = cursor.getString(nameCol);
                            long date = dateCol != -1 ? cursor.getLong(dateCol) * 1000L : 0;
                            long size = sizeCol != -1 ? cursor.getLong(sizeCol) : 0;
                            Uri uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                            if (name != null && seenNames.add(name)) {
                                list.add(new BackupEntry(name, uri, date, size));
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Logger.printException(() -> "Error querying MediaStore backups", t);
            }
        }

        // 2. Direct public downloads directory (both new and legacy)
        try {
            File publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
            addFilesFromDir(publicDir, list, seenNames);
        } catch (Throwable ignored) {}
        try {
            File legacyPublicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LEGACY_FOLDER);
            addFilesFromDir(legacyPublicDir, list, seenNames);
        } catch (Throwable ignored) {}

        // 3. App-specific downloads directory (both new and legacy)
        try {
            File appDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FOLDER);
            addFilesFromDir(appDir, list, seenNames);
        } catch (Throwable ignored) {}
        try {
            File legacyAppDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), LEGACY_FOLDER);
            addFilesFromDir(legacyAppDir, list, seenNames);
        } catch (Throwable ignored) {}

        // Sort descending by date modified
        Collections.sort(list, (a, b) -> Long.compare(b.modified, a.modified));
        return list;
    }

    private static void addFilesFromDir(File dir, List<BackupEntry> list, Set<String> seenNames) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name != null && name.toLowerCase(Locale.ROOT).endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    if (seenNames.add(f.getName())) {
                        list.add(new BackupEntry(f.getName(), Uri.fromFile(f), f.lastModified(), f.length()));
                    }
                }
            }
        }
    }

    /**
     * Reads all bytes from a given URI.
     */
    public static byte[] readBytes(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open input stream for: " + uri);
            return ConfigurationBackup.read(in);
        }
    }

    /**
     * Shares the backup file through the Android Share sheet.
     */
    public static void shareBackup(Context context, String fileName, Uri uri, byte[] bytes) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_SUBJECT, fileName);

            if (uri != null && "content".equals(uri.getScheme())) {
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                share.setClipData(android.content.ClipData.newRawUri(fileName, uri));
            } else {
                share.putExtra(Intent.EXTRA_TEXT, new String(bytes, StandardCharsets.UTF_8));
                share.setType("text/plain");
            }

            Intent chooser = Intent.createChooser(share, L10n.t("Share settings backup"));
            if (!(context instanceof Activity)) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(chooser);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not share backup", ex);
            Utils.showToastLong(L10n.t("Could not share backup: ") + ex.getMessage());
        }
    }

    /**
     * Checks if the system clipboard holds a HushGram configuration JSON string.
     */
    @Nullable
    public static String getClipboardBackup(Context context) {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    String str = text.toString().trim();
                    if (str.startsWith("{") && str.contains("\"project\"") &&
                            (str.contains("HushGram") || str.contains("InstagramPlus") || str.contains("Instagram-Plus"))) {
                        return str;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Copies the settings backup JSON string to the system clipboard.
     */
    public static void copyToClipboard(Context context, byte[] bytes) {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText(
                        "Instagram Plus Settings", new String(bytes, StandardCharsets.UTF_8));
                cm.setPrimaryClip(clip);
                Utils.showToastShort(L10n.t("Backup copied to clipboard"));
            }
        } catch (Throwable ignored) {}
    }
}

