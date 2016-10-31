package com.yalin.exoplayer.util;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class Util {
    public static final int SDK_INT =
            (Build.VERSION.SDK_INT == 23 && Build.VERSION.CODENAME.charAt(0) == 'N') ? 24
                    : Build.VERSION.SDK_INT;

    public static final String DEVICE = Build.DEVICE;

    public static final String MANUFACTURER = Build.MANUFACTURER;

    public static boolean maybeRequestReadExternalStoragePermission(Activity activity, Uri... uris) {
        if (SDK_INT < 23) {
            return false;
        }
        for (Uri uri : uris) {
            if (Util.isLocalFileUri(uri)) {
                if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isLocalFileUri(Uri uri) {
        String scheme = uri.getScheme();
        return TextUtils.isEmpty(scheme) || scheme.equals("file");
    }
}
