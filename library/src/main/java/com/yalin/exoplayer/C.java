package com.yalin.exoplayer;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class C {
    private C() {
    }

    public static final long TIME_UNSET = Long.MIN_VALUE + 1;

    public static final int INDEX_UNSET = -1;

    public static final int TRACK_TYPE_DEFAULT = 0;

    public static final int TRACK_TYPE_AUDIO = 1;

    public static final int TRACK_TYPE_VIDEO = 2;

    public static final int TRACK_TYPE_TEXT = 3;

    public static final int TRACK_TYPE_METADATA = 4;

    public static final int MSG_SET_SURFACE = 1;

    public static final int MSG_SET_VOLUME = 2;

    public static final int MSG_SET_PLAYBACK_PARAMS = 3;

    public static final int RESULT_END_OF_INPUT = -1;

    public static final int RESULT_MAX_LENGTH_EXCEEDED = -2;

    public static final int RESULT_NOTING_READ = -3;

    public static final int RESULT_BUFFER_READ = -4;

    public static final int RESULT_FORMAT_READ = -5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Format.NO_VALUE, STEREO_MODE_MONO, STEREO_MODE_TOP_BOTTOM, STEREO_MODE_LEFT_RIGHT})
    public @interface StereoMode {
    }

    public static final int STEREO_MODE_MONO = 0;

    public static final int STEREO_MODE_TOP_BOTTOM = 1;

    public static final int STEREO_MODE_LEFT_RIGHT = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Format.NO_VALUE, ENCODING_INVALID, ENCODING_PCM_8BIT, ENCODING_PCM_16BIT,
            ENCODING_PCM_24BIT, ENCODING_PCM_32BIT})
    public @interface PcmEncoding {
    }

    public static final int ENCODING_INVALID = AudioFormat.ENCODING_INVALID;

    public static final int ENCODING_PCM_8BIT = AudioFormat.ENCODING_PCM_8BIT;

    public static final int ENCODING_PCM_16BIT = AudioFormat.ENCODING_PCM_16BIT;

    public static final int ENCODING_PCM_24BIT = 0x80000000;

    public static final int ENCODING_PCM_32BIT = 0x40000000;

    @Retention(RetentionPolicy.SOURCE)
    public @interface SelectionFlags {
    }

    public static final int SELCTION_FLAG_DEFAULT = 1;

    public static final int SELCTION_FLAG_FORCED = 2;

    public static final int SELCTION_FLAG_AUTOSELECT = 4;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {BUFFER_FLAG_KEY_FRAME, BUFFER_FLAG_END_OF_STREAM,
            BUFFER_FLAG_ENCRYPTED, BUFFER_FLAG_DECODE_ONLY})
    public @interface BufferFlags {
    }

    @SuppressLint("InlinedApi")
    public static final int BUFFER_FLAG_KEY_FRAME = MediaCodec.BUFFER_FLAG_KEY_FRAME;

    @SuppressLint("InlinedApi")
    public static final int BUFFER_FLAG_END_OF_STREAM = MediaCodec.BUFFER_FLAG_END_OF_STREAM;

    public static final int BUFFER_FLAG_ENCRYPTED = 0x40000000;

    public static final int BUFFER_FLAG_DECODE_ONLY = 0x80000000;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({CRYPTO_MODE_UNENCRYPTED, CRYPTO_MODE_AEC_CTR, CRYPTO_MODE_AEC_CBC})
    public @interface CryptoMode {
    }

    @SuppressLint("InlinedApi")
    public static final int CRYPTO_MODE_UNENCRYPTED = MediaCodec.CRYPTO_MODE_UNENCRYPTED;

    @SuppressLint("InlinedApi")
    public static final int CRYPTO_MODE_AEC_CTR = MediaCodec.CRYPTO_MODE_AES_CTR;

    @SuppressLint("InlinedApi")
    public static final int CRYPTO_MODE_AEC_CBC = MediaCodec.CRYPTO_MODE_AES_CBC;

    public static long usToMs(long timeUs) {
        return timeUs == TIME_UNSET ? TIME_UNSET : (timeUs / 1000);
    }

    public static long msToUs(long timeMs) {
        return timeMs == TIME_UNSET ? TIME_UNSET : (timeMs * 1000);
    }
}
