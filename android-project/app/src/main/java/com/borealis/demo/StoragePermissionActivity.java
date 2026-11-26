package com.borealis.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

public class StoragePermissionActivity extends Activity {
    private static final int REQUEST_STORAGE = 7101;
    private boolean launched = false;

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void launchCompendium() {
        if (launched || !hasStorageAccess()) return;
        launched = true;
        startActivity(new Intent(this, DemoActivity.class));
        finish();
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQUEST_STORAGE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (hasStorageAccess()) {
            launchCompendium();
        } else {
            requestStorageAccess();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_STORAGE) {
            if (hasStorageAccess()) launchCompendium();
            else finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (hasStorageAccess()) launchCompendium();
            else finish();
        }
    }
}
