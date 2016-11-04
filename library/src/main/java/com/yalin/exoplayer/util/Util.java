package com.yalin.exoplayer.util;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.ExoPlayerLibraryInfo;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final String TAG = "Util";

    public static final int SDK_INT =
            (Build.VERSION.SDK_INT == 23 && Build.VERSION.CODENAME.charAt(0) == 'N') ? 24
                    : Build.VERSION.SDK_INT;

    public static final String DEVICE = Build.DEVICE;

    public static final String MANUFACTURER = Build.MANUFACTURER;

    public static final String MODEL = Build.MODEL;

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

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

    private static String getHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int i = 0;
        for (byte v : bytes) {
            hexChars[i++] = HEX_DIGITS[(v >> 4) & 0xf];
            hexChars[i++] = HEX_DIGITS[v & 0xf];
        }
        return new String(hexChars);
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

    public static String getCommaDelimitedSimpleClassName(Object[] objects) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < objects.length; i++) {
            stringBuilder.append(objects[i].getClass().getSimpleName());
            if (i < objects.length - 1) {
                stringBuilder.append(", ");
            }
        }
        return stringBuilder.toString();
    }

    public static int binarySearchCeil(long[] a, long value, boolean inclusive,
                                       boolean stayInBounds) {
        int index = Arrays.binarySearch(a, value);
        index = index < 0 ? ~index : (inclusive ? index : (index + 1));
        return stayInBounds ? Math.min(a.length - 1, index) : index;
    }

    public static int ceilDivide(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    public static void scaleLargeTimestampsInPlace(long[] timestamps, long multiplier, long divisor) {
        if (divisor >= multiplier && (divisor % multiplier) == 0) {
            long divisionFactor = divisor / multiplier;
            for (int i = 0; i < timestamps.length; i++) {
                timestamps[i] /= divisionFactor;
            }
        } else if (divisor < multiplier && (multiplier % divisor) == 0) {
            long multiplicationFactor = multiplier / divisor;
            for (int i = 0; i < timestamps.length; i++) {
                timestamps[i] *= multiplicationFactor;
            }
        } else {
            double multiplicationFactor = (double) multiplier / divisor;
            for (int i = 0; i < timestamps.length; i++) {
                timestamps[i] = (long) (timestamps[i] * multiplicationFactor);
            }
        }
    }

    public static String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = input.getBytes("UTF-8");
            digest.update(bytes, 0, bytes.length);
            return getHexString(digest.digest());
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getDefaultBufferSize(int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_DEFAULT:
                return C.DEFAULT_MUXED_BUFFER_SIZE;
            case C.TRACK_TYPE_AUDIO:
                return C.DEFAULT_AUDIO_BUFFER_SIZE;
            case C.TRACK_TYPE_VIDEO:
                return C.DEFAULT_VIDEO_BUFFER_SIZE;
            case C.TRACK_TYPE_TEXT:
                return C.DEFAULT_TEXT_BUFFER_SIZE;
            case C.TRACK_TYPE_METADATA:
                return C.DEFAULT_METADATA_BUFFER_SIZE;
            default:
                throw new IllegalStateException();
        }
    }

    public static String normalizeLanguageCode(String language) {
        return language == null ? null : new Locale(language).getLanguage();
    }

    public static Point getPhysicalDisplaySize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return getPhysicalDisplaySize(context, windowManager.getDefaultDisplay());
    }

    public static Point getPhysicalDisplaySize(Context context, Display display) {
        if (Util.SDK_INT < 25 && display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            // Before API 25 the Display object does not provide a working way to identify Android TVs
            // that can show 4k resolution in a SurfaceView, so check for supported devices here.
            if ("Sony".equals(Util.MANUFACTURER) && Util.MODEL.startsWith("BRAVIA")
                    && context.getPackageManager().hasSystemFeature("com.sony.dtv.hardware.panel.qfhd")) {
                return new Point(3840, 2160);
            } else if ("NVIDIA".equals(Util.MANUFACTURER) && Util.MODEL.contains("SHIELD")) {
                // Attempt to read sys.display-size.
                String sysDisplaySize = null;
                try {
                    Class<?> systemProperties = Class.forName("android.os.SystemProperties");
                    Method getMethod = systemProperties.getMethod("get", String.class);
                    sysDisplaySize = (String) getMethod.invoke(systemProperties, "sys.display-size");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read sys.display-size", e);
                }
                // If we managed to read sys.display-size, attempt to parse it.
                if (!TextUtils.isEmpty(sysDisplaySize)) {
                    try {
                        String[] sysDisplaySizeParts = sysDisplaySize.trim().split("x");
                        if (sysDisplaySizeParts.length == 2) {
                            int width = Integer.parseInt(sysDisplaySizeParts[0]);
                            int height = Integer.parseInt(sysDisplaySizeParts[1]);
                            if (width > 0 && height > 0) {
                                return new Point(width, height);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // Do nothing.
                    }
                    Log.e(TAG, "Invalid sys.display-size: " + sysDisplaySize);
                }
            }
        }

        Point displaySize = new Point();
        if (Util.SDK_INT >= 23) {
            getDisplaySizeV23(display, displaySize);
        } else if (Util.SDK_INT >= 17) {
            getDisplaySizeV17(display, displaySize);
        } else if (Util.SDK_INT >= 16) {
            getDisplaySizeV16(display, displaySize);
        } else {
            getDisplaySizeV9(display, displaySize);
        }
        return displaySize;
    }

    @TargetApi(23)
    private static void getDisplaySizeV23(Display display, Point outSize) {
        Display.Mode mode = display.getMode();
        outSize.x = mode.getPhysicalWidth();
        outSize.y = mode.getPhysicalHeight();
    }

    @TargetApi(17)
    private static void getDisplaySizeV17(Display display, Point outSize) {
        display.getRealSize(outSize);
    }

    @TargetApi(16)
    private static void getDisplaySizeV16(Display display, Point outSize) {
        display.getSize(outSize);
    }

    @SuppressWarnings("deprecation")
    private static void getDisplaySizeV9(Display display, Point outSize) {
        outSize.x = display.getWidth();
        outSize.y = display.getHeight();
    }

    public static int[] toArray(List<Integer> list) {
        if (list == null) {
            return null;
        }
        int length = list.size();
        int[] intArray = new int[length];
        for (int i = 0; i < length; i++) {
            intArray[i] = list.get(i);
        }
        return intArray;
    }
}
