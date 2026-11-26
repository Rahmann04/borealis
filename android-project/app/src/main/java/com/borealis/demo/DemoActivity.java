package com.borealis.demo;

import android.os.Bundle;
import android.content.res.AssetManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.app.AlertDialog;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.libsdl.app.BorealisHandler;
import org.libsdl.app.PlatformUtils;
import org.libsdl.app.SDLActivity;

public class DemoActivity extends SDLActivity {

    private static final int REQUEST_IMPORT_STORAGE = 0xC041;

    private static final String P5R_PREFS = "compendium_p5r_launcher";
    private static final String P5R_LAST_PACKAGE = "last_package";

    private static class P5RApp {
        String label, packageName;
        P5RApp(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
        @Override public String toString() {
            return label + "\n" + packageName;
        }
    }

    private ArrayList<P5RApp> findP5RLaunchers() {
        ArrayList<P5RApp> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            String label;
            try {
                label = String.valueOf(info.loadLabel(pm));
            } catch (Exception e) {
                label = pkg;
            }

            String key = (label + " " + pkg).toLowerCase(Locale.ROOT)
                    .replace("_", " ").replace("-", " ");
            boolean match = key.contains("persona 5") ||
                    key.contains("persona5") || key.contains("p5r") ||
                    key.contains("royal");
            if (!match) continue;

            boolean duplicate = false;
            for (P5RApp app : apps) {
                if (app.packageName.equals(pkg)) duplicate = true;
            }
            if (!duplicate) apps.add(new P5RApp(label, pkg));
        }

        Collections.sort(apps, new Comparator<P5RApp>() {
            @Override public int compare(P5RApp a, P5RApp b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return apps;
    }

    private boolean launchP5RPackage(String pkg) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) return false;
            startActivity(intent);
            getSharedPreferences(P5R_PREFS, MODE_PRIVATE).edit()
                    .putString(P5R_LAST_PACKAGE, pkg).apply();
            return true;
        } catch (Exception e) {
            CompendiumLogger.log("WARN", "P5R_LAUNCH_FAILED", e.toString());
            return false;
        }
    }

    public void launchP5R() {
        runOnUiThread(() -> {
            ArrayList<P5RApp> apps = findP5RLaunchers();
            if (apps.isEmpty()) {
                Toast.makeText(this, "Aplikasi Persona 5 Royal tidak ditemukan",
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (apps.size() == 1) {
                if (!launchP5RPackage(apps.get(0).packageName)) {
                    Toast.makeText(this, "Gagal membuka " + apps.get(0).label,
                            Toast.LENGTH_LONG).show();
                }
                return;
            }

            String[] choices = new String[apps.size()];
            for (int i = 0; i < apps.size(); i++)
                choices[i] = apps.get(i).toString();

            new AlertDialog.Builder(this)
                    .setTitle("Pilih Persona 5 Royal")
                    .setItems(choices, (dialog, which) -> {
                        P5RApp selected = apps.get(which);
                        if (!launchP5RPackage(selected.packageName)) {
                            Toast.makeText(this, "Gagal membuka " + selected.label,
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        });
    }


    public void openStoragePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
            "application/zip",
            "application/x-7z-compressed",
            "application/vnd.rar",
            "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMPORT_STORAGE);
    }

    private String getSelectedFileName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(
                uri, new String[] { OpenableColumns.DISPLAY_NAME },
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception error) {
            CompendiumLogger.log("WARN", "IMPORT_NAME_FAILED", error.toString());
        }

        if (name == null || name.trim().isEmpty()) {
            name = "imported_mod";
        }

        // Keep only a filename; never allow a provider-supplied path.
        name = name.replaceAll("[\\\\/\\\\\\\\]", "_");
        name = name.replace("..", "_");
        return name;
    }

    private boolean isSupportedImportFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".7z") ||
               lower.endsWith(".rar") || lower.endsWith(".cpk");
    }

    private void importSelectedStorageFile(Uri uri) {
        String name = getSelectedFileName(uri);
        if (!isSupportedImportFile(name)) {
            Toast.makeText(this, "Pilih file ZIP, 7Z, RAR, atau CPK",
                    Toast.LENGTH_LONG).show();
            return;
        }

        File importDir = new File(getCacheDir(), "compendium-imports");
        if (!importDir.exists() && !importDir.mkdirs()) {
            Toast.makeText(this, "Tidak dapat menyiapkan file impor",
                    Toast.LENGTH_LONG).show();
            return;
        }

        File destination = new File(importDir,
                System.currentTimeMillis() + "_" + name);

        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IOException("Cannot open selected file");

            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            nativeOnStorageFileSelected(destination.getAbsolutePath());
            CompendiumLogger.log("INFO", "STORAGE_FILE_SELECTED",
                    "Selected archive copied for Compendium import: " + name);
        } catch (Exception error) {
            if (destination.exists()) destination.delete();
            CompendiumLogger.log("ERROR", "STORAGE_IMPORT_FAILED", error.toString());
            Toast.makeText(this, "Gagal membaca file: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private native void nativeOnStorageFileSelected(String path);

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_STORAGE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            CompendiumLogger.log("INFO", "STORAGE_IMPORT_CANCELLED",
                    "Storage file selection cancelled");
            return;
        }
        importSelectedStorageFile(data.getData());
    }

    private void copyAssetDirectory(String assetPath, File destination)
            throws IOException {
        AssetManager assets = getAssets();
        String[] entries = assets.list(assetPath);

        if (entries != null && entries.length > 0) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new IOException("Cannot create: " + destination);
            }
            for (String entry : entries) {
                copyAssetDirectory(
                    assetPath + "/" + entry,
                    new File(destination, entry)
                );
            }
            return;
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create: " + parent);
        }

        try (InputStream input = assets.open(assetPath);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }

    private void prepareAppResources() throws IOException {
        File resourceRoot = new File(getFilesDir(), "resources");
        copyAssetDirectory("resources", resourceRoot);
        CompendiumLogger.log(
            "INFO", "RESOURCES_EXTRACTED",
            "App resources copied to internal storage"
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CompendiumLogger.initialize(this);
        try {
            prepareAppResources();
        } catch (IOException error) {
            CompendiumLogger.log("ERROR", "RESOURCE_EXTRACTION_FAILED",
                    error.toString());
            throw new RuntimeException("Unable to prepare app resources", error);
        }
        CompendiumLogger.installCrashLogger();
        CompendiumLogger.log("INFO", "ACTIVITY_CREATE_START", "Starting SDLActivity");
        super.onCreate(savedInstanceState);
        CompendiumLogger.log("INFO", "ACTIVITY_CREATE_SUCCESS", "SDLActivity initialized");
        PlatformUtils.borealisHandler = new BorealisHandler();
        CompendiumLogger.log("INFO", "BOREALIS_HANDLER_READY", "BorealisHandler initialized");
    }

    @Override
    protected void onDestroy() {
        CompendiumLogger.log("INFO", "ACTIVITY_DESTROY", "Activity destroyed");
        super.onDestroy();
        System.exit(0);
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL2", "Compendium"};
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }
}
