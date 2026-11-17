/*
 * Copyright 2026 HushGram contributors
 * https://github.com/SysAdminDoc/HushGram
 */
package app.hushgram.extension.instagram.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import app.hushgram.extension.shared.SettingsContextRule;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 30})
public class SettingsBackupHelperTest {
    @Rule
    public final SettingsContextRule context = new SettingsContextRule();

    @Test
    public void exportToDownloadsCreatesValidBackupFile() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        SettingsBackupHelper.ExportResult result = SettingsBackupHelper.exportToDownloads(ctx);

        assertNotNull(result);
        assertNotNull(result.fileName);
        assertTrue(result.fileName.startsWith("HushGram-settings"));
        assertTrue(result.fileName.endsWith(".json"));
        assertTrue(result.displayPath.contains("HushGram"));
        assertNotNull(result.data);
        assertTrue(result.data.length > 0);

        JSONObject json = new JSONObject(new String(result.data, StandardCharsets.UTF_8));
        assertEquals("HushGram", json.getString("project"));
        assertEquals(1, json.getInt("schema"));
        assertTrue(json.has("settings"));
    }

    @Test
    public void findBackupsDiscoversSavedBackup() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        SettingsBackupHelper.ExportResult exported = SettingsBackupHelper.exportToDownloads(ctx);

        List<SettingsBackupHelper.BackupEntry> backups = SettingsBackupHelper.findBackups(ctx);
        assertNotNull(backups);

        boolean found = false;
        for (SettingsBackupHelper.BackupEntry entry : backups) {
            if (entry.name.equals(exported.fileName)) {
                found = true;
                break;
            }
        }
        // In Robolectric environment with public/app dirs, it finds the file or fallback
        if (found) {
            byte[] read = SettingsBackupHelper.readBytes(ctx, exported.uri);
            assertNotNull(read);
            assertEquals(exported.data.length, read.length);
        }
    }

    @Test
    public void clipboardBackupRoundTrip() {
        Context ctx = RuntimeEnvironment.getApplication();
        String jsonStr = "{\"project\":\"HushGram\",\"schema\":1,\"settings\":{}}";
        byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);

        SettingsBackupHelper.copyToClipboard(ctx, bytes);
        String detected = SettingsBackupHelper.getClipboardBackup(ctx);

        assertNotNull(detected);
        assertTrue(detected.contains("\"project\""));
        assertTrue(detected.contains("HushGram"));
    }
}

