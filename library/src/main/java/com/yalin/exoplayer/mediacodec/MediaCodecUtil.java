package com.yalin.exoplayer.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecList;
import android.util.Log;

import com.yalin.exoplayer.util.MimeTypes;
import com.yalin.exoplayer.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */
@TargetApi(16)
public final class MediaCodecUtil {
    public static class DecoderQueryException extends Exception {
        private DecoderQueryException(Throwable cause) {
            super("Failed to query underlying media codecs", cause);
        }
    }

    private static final String TAG = "MediaCodecUtil";
    private static final MediaCodecInfo PASSTHROUGH_DECODER_INFO =
            MediaCodecInfo.newPassthroughInstance("OMX.google.raw.decoder");

    private static final HashMap<CodecKey, List<MediaCodecInfo>> decoderInfosCache = new HashMap<>();

    public static MediaCodecInfo getDecoderInfo(String mimeType, boolean secure)
            throws DecoderQueryException {
        List<MediaCodecInfo> decoderInfos = getDecoderInfos(mimeType, secure);
        return decoderInfos.isEmpty() ? null : decoderInfos.get(0);
    }

    public static synchronized List<MediaCodecInfo> getDecoderInfos(String mimeType,
                                                                    boolean secure) throws DecoderQueryException {
        CodecKey key = new CodecKey(mimeType, secure);
        List<MediaCodecInfo> decoderInfos = decoderInfosCache.get(key);
        if (decoderInfos != null) {
            return decoderInfos;
        }
        MediaCodecListCompat mediaCodecList = Util.SDK_INT >= 21
                ? new MediaCodecListCompatV21(secure) : new MediaCodecListCompatV16();
        decoderInfos = getDecoderInfosInternal(key, mediaCodecList);
        if (secure && decoderInfos.isEmpty() && 21 <= Util.SDK_INT && Util.SDK_INT <= 23) {
            mediaCodecList = new MediaCodecListCompatV16();
            decoderInfos = getDecoderInfosInternal(key, mediaCodecList);
            if (!decoderInfos.isEmpty()) {
                Log.w(TAG, "MediaCodecList API didn't list secure decoder for: " + mimeType
                        + ". Assuming: " + decoderInfos.get(0).name);
            }
        }
        decoderInfos = Collections.unmodifiableList(decoderInfos);
        decoderInfosCache.put(key, decoderInfos);
        return decoderInfos;
    }

    private static List<MediaCodecInfo> getDecoderInfosInternal(
            CodecKey key, MediaCodecListCompat mediaCodecList)
            throws DecoderQueryException {
        try {
            List<MediaCodecInfo> decoderInfos = new ArrayList<>();
            String mimeType = key.mimeType;
            int numberOfCodecs = mediaCodecList.getCodecCount();
            boolean secureDecodersExplicit = mediaCodecList.secureDecodersExplicit();
            for (int i = 0; i < numberOfCodecs; i++) {
                android.media.MediaCodecInfo codecInfo = mediaCodecList.getCodecInfoAt(i);
                String codecName = codecInfo.getName();
                if (isCodecUsableDecoder(codecInfo, codecName, secureDecodersExplicit)) {
                    for (String supportedType : codecInfo.getSupportedTypes()) {
                        try {
                            CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(supportedType);
                            boolean secure = mediaCodecList.isSecurePlaybackSupported(mimeType, capabilities);
                            if ((secureDecodersExplicit && key.secure == secure)
                                    || (!secureDecodersExplicit && !key.secure)) {
                                decoderInfos.add(
                                        MediaCodecInfo.newInstance(codecName, mimeType, capabilities));
                            } else if (!secureDecodersExplicit && secure) {
                                decoderInfos.add(MediaCodecInfo.newInstance(codecName + ".secure",
                                        mimeType, capabilities));
                                return decoderInfos;
                            }
                        } catch (Exception e) {
                            if (Util.SDK_INT <= 23 && !decoderInfos.isEmpty()) {
                                Log.e(TAG, "Skipping codec " + codecName + " (failed to query capabilities)");
                            } else {
                                Log.e(TAG, "Failed to query codec " + codecName + " (" + supportedType + ")");
                                throw e;
                            }
                        }
                    }
                }
            }
            return decoderInfos;
        } catch (Exception e) {
            throw new DecoderQueryException(e);
        }
    }

    private static boolean isCodecUsableDecoder(android.media.MediaCodecInfo info, String name,
                                                boolean secureDecodersExplicit) {
        if (info.isEncoder() || (!secureDecodersExplicit && name.endsWith(".secure"))) {
            return false;
        }

        if (Util.SDK_INT < 21
                && ("CIPAACDecoder".equals(name)
                || "CIPMP3Decoder".equals(name)
                || "CIPVorbisDecoder".equals(name)
                || "AACDecoder".equals(name)
                || "MP3Decoder".equals(name))) {
            return false;
        }

        if (Util.SDK_INT < 18 && "OMX.SEC.MP3.Decoder".equals(name)) {
            return false;
        }

        if (Util.SDK_INT < 18 && "OMX.MTK.AUDIO.DECODER.AAC".equals(name)
                && "a70".equals(Util.DEVICE)) {
            return false;
        }

        if (Util.SDK_INT == 16
                && "OMX.qcom.audio.decoder.mp3".equals(name)
                && ("dlxu".equals(Util.DEVICE)
                || "protou".equals(Util.DEVICE)
                || "ville".equals(Util.DEVICE)
                || "villeplus".equals(Util.DEVICE)
                || "villec2".equals(Util.DEVICE)
                || Util.DEVICE.startsWith("gee")
                || "C6602".equals(Util.DEVICE)
                || "C6603".equals(Util.DEVICE)
                || "C6606".equals(Util.DEVICE)
                || "C6616".equals(Util.DEVICE)
                || "L36h".equals(Util.DEVICE)
                || "SO-02E".equals(Util.DEVICE))) {
            return false;
        }

        if (Util.SDK_INT == 16
                && "OMX.qcom.audio.decoder.aac".equals(name)
                && ("C1504".equals(Util.DEVICE)
                || "C1505".equals(Util.DEVICE)
                || "C1604".equals(Util.DEVICE)
                || "C1605".equals(Util.DEVICE))) {
            return false;
        }

        if (Util.SDK_INT <= 19
                && (Util.DEVICE.startsWith("d2") || Util.DEVICE.startsWith("serrano")
                || Util.DEVICE.startsWith("jflte") || Util.DEVICE.startsWith("santos"))
                && "samsung".equals(Util.MANUFACTURER) && "OMX.SEC.vp8.dec".equals(name)) {
            return false;
        }

        //noinspection RedundantIfStatement
        if (Util.SDK_INT <= 19 && Util.DEVICE.startsWith("jflte")
                && "OMX.qcom.video.decoder.vp8".equals(name)) {
            return false;
        }

        return true;
    }

    public static MediaCodecInfo getPassthroughDecoderInfo() {
        return PASSTHROUGH_DECODER_INFO;
    }

    private interface MediaCodecListCompat {
        int getCodecCount();

        android.media.MediaCodecInfo getCodecInfoAt(int index);

        boolean secureDecodersExplicit();

        boolean isSecurePlaybackSupported(String mimeType, CodecCapabilities capabilities);
    }

    @TargetApi(21)
    private static final class MediaCodecListCompatV21 implements MediaCodecListCompat {

        private final int codecKind;

        private android.media.MediaCodecInfo[] mediaCodecInfos;

        public MediaCodecListCompatV21(boolean includeSecure) {
            this.codecKind = includeSecure ? MediaCodecList.ALL_CODECS : MediaCodecList.REGULAR_CODECS;
        }

        @Override
        public int getCodecCount() {
            ensureMediaCodecInfosInitialized();
            return mediaCodecInfos.length;
        }

        @Override
        public android.media.MediaCodecInfo getCodecInfoAt(int index) {
            ensureMediaCodecInfosInitialized();
            return mediaCodecInfos[index];
        }

        @Override
        public boolean secureDecodersExplicit() {
            return true;
        }

        @Override
        public boolean isSecurePlaybackSupported(String mimeType, CodecCapabilities capabilities) {
            return capabilities.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback);
        }

        private void ensureMediaCodecInfosInitialized() {
            if (mediaCodecInfos == null) {
                mediaCodecInfos = new MediaCodecList(codecKind).getCodecInfos();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static final class MediaCodecListCompatV16 implements MediaCodecListCompat {

        @Override
        public int getCodecCount() {
            return MediaCodecList.getCodecCount();
        }

        @Override
        public android.media.MediaCodecInfo getCodecInfoAt(int index) {
            return MediaCodecList.getCodecInfoAt(index);
        }

        @Override
        public boolean secureDecodersExplicit() {
            return false;
        }

        @Override
        public boolean isSecurePlaybackSupported(String mimeType, CodecCapabilities capabilities) {
            return MimeTypes.VIDEO_H264.equals(mimeType);
        }
    }

    private static final class CodecKey {
        public final String mimeType;
        public final boolean secure;

        public CodecKey(String mimeType, boolean secure) {
            this.mimeType = mimeType;
            this.secure = secure;
        }
    }
}
