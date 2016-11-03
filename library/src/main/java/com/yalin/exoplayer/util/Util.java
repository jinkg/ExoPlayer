package com.yalin.exoplayer.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.yalin.exoplayer.ExoPlayerLibraryInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

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

    public static final String MODEL = Build.MODEL;

    public static ExecutorService newSingleThreadExecutor(final String threadName) {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r, threadName);
            }
        });
    }

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

    public static String toLowerInvariant(String text) {
        return text == null ? null : text.toLowerCase(Locale.US);
    }

    public static boolean areEqual(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    public static boolean isLocalFileUri(Uri uri) {
        String scheme = uri.getScheme();
        return TextUtils.isEmpty(scheme) || scheme.equals("file");
    }

    public static int binarySearchFloor(long[] a, long value, boolean inclusive,
                                        boolean stayInBounds) {
        int index = Arrays.binarySearch(a, value);
        index = index < 0 ? -(index + 2) : (inclusive ? index : (index - 1));
        return stayInBounds ? Math.max(0, index) : index;
    }

    public static byte[] getBytesFromHexString(String hexString) {
        byte[] data = new byte[hexString.length() / 2];
        for (int i = 0; i < data.length; i++) {
            int stringOffset = i * 2;
            data[i] = (byte) ((Character.digit(hexString.charAt(stringOffset), 16) << 4)
                    + Character.digit(hexString.charAt(stringOffset + 1), 16));
        }
        return data;
    }

    public static long scaleLargeTimestamp(long timestamp, long multiplier, long divisor) {
        if (divisor >= multiplier && (divisor % multiplier) == 0) {
            long divisionFactor = divisor / multiplier;
            return timestamp / divisionFactor;
        } else if (divisor < multiplier && (multiplier % divisor) == 0) {
            long multiplicationFactor = multiplier / divisor;
            return timestamp * multiplicationFactor;
        } else {
            double multiplicationFactor = (double) multiplier / divisor;
            return (long) (timestamp * multiplicationFactor);
        }
    }

    public static long[] scaleLargeTimestamps(List<Long> timestamps, long multiplier, long divisor) {
        long[] scaledTimestamps = new long[timestamps.size()];
        if (divisor >= multiplier && (divisor % multiplier) == 0) {
            long divisionFactor = divisor / multiplier;
            for (int i = 0; i < scaledTimestamps.length; i++) {
                scaledTimestamps[i] = timestamps.get(i) / divisionFactor;
            }
        } else if (divisor < multiplier && (multiplier % divisor) == 0) {
            long multiplicationFactor = multiplier / divisor;
            for (int i = 0; i < scaledTimestamps.length; i++) {
                scaledTimestamps[i] = timestamps.get(i) * multiplicationFactor;
            }
        } else {
            double multiplicationFactor = (double) multiplier / divisor;
            for (int i = 0; i < scaledTimestamps.length; i++) {
                scaledTimestamps[i] = (long) (timestamps.get(i) * multiplicationFactor);
            }
        }
        return scaledTimestamps;
    }

    public static String getUserAgent(Context context, String applicationName) {
        String versionName;
        try {
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            versionName = info.versionName;
        } catch (NameNotFoundException e) {
            versionName = "?";
        }
        return applicationName + "/" + versionName + " (Linux;Android " + Build.VERSION.RELEASE
                + ") " + "ExoPlayerLib/" + ExoPlayerLibraryInfo.VERSION;
    }

    public static int getIntegerCodeForString(String string) {
        int length = string.length();
        Assertions.checkArgument(length <= 4);
        int result = 0;
        for (int i = 0; i < length; i++) {
            result <<= 8;
            result |= string.charAt(i);
        }
        return result;
    }
}
