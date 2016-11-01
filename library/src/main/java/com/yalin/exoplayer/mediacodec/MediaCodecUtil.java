package com.yalin.exoplayer.mediacodec;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.util.Log;
import android.util.Pair;
import android.util.SparseIntArray;

import com.yalin.exoplayer.util.MimeTypes;
import com.yalin.exoplayer.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */
@TargetApi(16)
@SuppressLint("InlinedApi")
public final class MediaCodecUtil {
    public static class DecoderQueryException extends Exception {
        private DecoderQueryException(Throwable cause) {
            super("Failed to query underlying media codecs", cause);
        }
    }

    private static final String TAG = "MediaCodecUtil";
    private static final MediaCodecInfo PASSTHROUGH_DECODER_INFO =
            MediaCodecInfo.newPassthroughInstance("OMX.google.raw.decoder");
    private static final Pattern PROFILE_PATTERN = Pattern.compile("^\\D?(\\d+)$");

    private static final SparseIntArray AVC_PROFILE_NUMBER_TO_CONST;
    private static final SparseIntArray AVC_LEVEL_NUMBER_TO_CONST;
    private static final String CODEC_ID_AVC1 = "avc1";
    private static final String CODEC_ID_AVC2 = "avc2";

    private static final Map<String, Integer> HEVC_CODEC_STRING_TO_PROFILE_LEVEL;
    private static final String CODEC_ID_HEV1 = "hev1";
    private static final String CODEC_ID_HVC1 = "hvc1";

    private static int maxH264DecodeableFrameSize = -1;

    private static final HashMap<CodecKey, List<MediaCodecInfo>> decoderInfosCache = new HashMap<>();

    private MediaCodecUtil() {
    }

    public static MediaCodecInfo getDecoderInfo(String mimeType, boolean secure)
            throws DecoderQueryException {
        List<MediaCodecInfo> decoderInfos = getDecoderInfos(mimeType, secure);
        return decoderInfos.isEmpty() ? null : decoderInfos.get(0);
    }

    public static MediaCodecInfo getPassthroughDecoderInfo() {
        return PASSTHROUGH_DECODER_INFO;
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

    public static Pair<Integer, Integer> getCodecProfileAndLevel(String codec) {
        if (codec == null) {
            return null;
        }
        String[] parts = codec.split("\\.");
        switch (parts[0]) {
            case CODEC_ID_HEV1:
            case CODEC_ID_HVC1:
                return getHevcProfileAndLevel(codec, parts);
            case CODEC_ID_AVC1:
            case CODEC_ID_AVC2:
                return getAvcProfileAndLevel(codec, parts);
            default:
                return null;
        }
    }

    public static int maxH264DecodableFrameSize() throws DecoderQueryException {
        if (maxH264DecodeableFrameSize == -1) {
            int result = 0;
            MediaCodecInfo decoderInfo = getDecoderInfo(MimeTypes.VIDEO_H264, false);
            if (decoderInfo != null) {
                for (CodecProfileLevel profileLevel : decoderInfo.getprofileLevels()) {
                    result = Math.max(avcLevelToMaxFrameSize(profileLevel.level), result);
                }
                result = Math.max(result, 480 * 360);
            }
            maxH264DecodeableFrameSize = result;
        }
        return maxH264DecodeableFrameSize;
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

    private static Pair<Integer, Integer> getHevcProfileAndLevel(String codec, String[] parts) {
        if (parts.length < 4) {
            Log.w(TAG, "Ignoring malformed HEVC codec string: " + codec);
            return null;
        }
        Matcher matcher = PROFILE_PATTERN.matcher(parts[1]);
        if (!matcher.matches()) {
            Log.w(TAG, "Ignoring malformed HEVC codec string: " + codec);
            return null;
        }
        String profileString = matcher.group(1);
        int profile;
        if ("1".equals(profileString)) {
            profile = CodecProfileLevel.HEVCProfileMain;
        } else if ("2".equals(profileString)) {
            profile = CodecProfileLevel.HEVCProfileMain10;
        } else {
            Log.w(TAG, "Unknown HEVC profile string: " + profileString);
            return null;
        }
        Integer level = HEVC_CODEC_STRING_TO_PROFILE_LEVEL.get(parts[3]);
        if (level == null) {
            Log.w(TAG, "Unkown HEVC level string: " + matcher.group(1));
            return null;
        }
        return new Pair<>(profile, level);
    }

    private static Pair<Integer, Integer> getAvcProfileAndLevel(String codec, String[] codecsParts) {
        if (codec.length() < 2) {
            Log.w(TAG, "Ignoring malformed AVC codec string: " + codec);
            return null;
        }
        Integer profileInteger;
        Integer levelInteger;
        try {
            if (codecsParts[1].length() == 6) {
                profileInteger = Integer.parseInt(codecsParts[1].substring(0, 2), 16);
                levelInteger = Integer.parseInt(codecsParts[1].substring(4), 16);
            } else if (codecsParts.length >= 3) {
                profileInteger = Integer.parseInt(codecsParts[1]);
                levelInteger = Integer.parseInt(codecsParts[2]);
            } else {
                Log.w(TAG, "Ignoring malformed AVC codec string: " + codec);
                return null;
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Ignoring malformed AVC codec string: " + codec);
            return null;
        }

        Integer profile = AVC_PROFILE_NUMBER_TO_CONST.get(profileInteger);
        if (profile == 0) {
            Log.w(TAG, "Unknown AVC profile: " + profileInteger);
            return null;
        }
        Integer level = AVC_LEVEL_NUMBER_TO_CONST.get(levelInteger);
        if (level == 0) {
            Log.w(TAG, "Unknown AVC level: " + level);
            return null;
        }
        return new Pair<>(profile, level);
    }

    private static int avcLevelToMaxFrameSize(int avcLevel) {
        switch (avcLevel) {
            case CodecProfileLevel.AVCLevel1:
                return 99 * 16 * 16;
            case CodecProfileLevel.AVCLevel1b:
                return 99 * 16 * 16;
            case CodecProfileLevel.AVCLevel12:
                return 396 * 16 * 16;
            case CodecProfileLevel.AVCLevel13:
                return 396 * 16 * 16;
            case CodecProfileLevel.AVCLevel2:
                return 396 * 16 * 16;
            case CodecProfileLevel.AVCLevel21:
                return 792 * 16 * 16;
            case CodecProfileLevel.AVCLevel22:
                return 1620 * 16 * 16;
            case CodecProfileLevel.AVCLevel3:
                return 1620 * 16 * 16;
            case CodecProfileLevel.AVCLevel31:
                return 3600 * 16 * 16;
            case CodecProfileLevel.AVCLevel32:
                return 5120 * 16 * 16;
            case CodecProfileLevel.AVCLevel4:
                return 8192 * 16 * 16;
            case CodecProfileLevel.AVCLevel41:
                return 8192 * 16 * 16;
            case CodecProfileLevel.AVCLevel42:
                return 8704 * 16 * 16;
            case CodecProfileLevel.AVCLevel5:
                return 22080 * 16 * 16;
            case CodecProfileLevel.AVCLevel51:
                return 36864 * 16 * 16;
            default:
                return -1;
        }
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

    static {
        AVC_PROFILE_NUMBER_TO_CONST = new SparseIntArray();
        AVC_PROFILE_NUMBER_TO_CONST.put(66, CodecProfileLevel.AVCProfileBaseline);
        AVC_PROFILE_NUMBER_TO_CONST.put(77, CodecProfileLevel.AVCProfileMain);
        AVC_PROFILE_NUMBER_TO_CONST.put(88, CodecProfileLevel.AVCProfileExtended);
        AVC_PROFILE_NUMBER_TO_CONST.put(100, CodecProfileLevel.AVCProfileHigh);

        AVC_LEVEL_NUMBER_TO_CONST = new SparseIntArray();
        AVC_LEVEL_NUMBER_TO_CONST.put(10, CodecProfileLevel.AVCLevel1);

        AVC_LEVEL_NUMBER_TO_CONST.put(11, CodecProfileLevel.AVCLevel11);
        AVC_LEVEL_NUMBER_TO_CONST.put(12, CodecProfileLevel.AVCLevel12);
        AVC_LEVEL_NUMBER_TO_CONST.put(13, CodecProfileLevel.AVCLevel13);
        AVC_LEVEL_NUMBER_TO_CONST.put(20, CodecProfileLevel.AVCLevel2);
        AVC_LEVEL_NUMBER_TO_CONST.put(21, CodecProfileLevel.AVCLevel21);
        AVC_LEVEL_NUMBER_TO_CONST.put(22, CodecProfileLevel.AVCLevel22);
        AVC_LEVEL_NUMBER_TO_CONST.put(30, CodecProfileLevel.AVCLevel3);
        AVC_LEVEL_NUMBER_TO_CONST.put(31, CodecProfileLevel.AVCLevel31);
        AVC_LEVEL_NUMBER_TO_CONST.put(32, CodecProfileLevel.AVCLevel32);
        AVC_LEVEL_NUMBER_TO_CONST.put(40, CodecProfileLevel.AVCLevel4);
        AVC_LEVEL_NUMBER_TO_CONST.put(41, CodecProfileLevel.AVCLevel41);
        AVC_LEVEL_NUMBER_TO_CONST.put(42, CodecProfileLevel.AVCLevel42);
        AVC_LEVEL_NUMBER_TO_CONST.put(50, CodecProfileLevel.AVCLevel5);
        AVC_LEVEL_NUMBER_TO_CONST.put(51, CodecProfileLevel.AVCLevel51);
        AVC_LEVEL_NUMBER_TO_CONST.put(52, CodecProfileLevel.AVCLevel52);

        HEVC_CODEC_STRING_TO_PROFILE_LEVEL = new HashMap<>();
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L30", CodecProfileLevel.HEVCMainTierLevel1);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L60", CodecProfileLevel.HEVCMainTierLevel2);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L63", CodecProfileLevel.HEVCMainTierLevel21);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L90", CodecProfileLevel.HEVCMainTierLevel3);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L93", CodecProfileLevel.HEVCMainTierLevel31);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L120", CodecProfileLevel.HEVCMainTierLevel4);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L123", CodecProfileLevel.HEVCMainTierLevel41);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L150", CodecProfileLevel.HEVCMainTierLevel5);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L153", CodecProfileLevel.HEVCMainTierLevel51);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L156", CodecProfileLevel.HEVCMainTierLevel52);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L180", CodecProfileLevel.HEVCMainTierLevel6);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L183", CodecProfileLevel.HEVCMainTierLevel61);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("L186", CodecProfileLevel.HEVCMainTierLevel62);

        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H30", CodecProfileLevel.HEVCHighTierLevel1);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H60", CodecProfileLevel.HEVCHighTierLevel2);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H63", CodecProfileLevel.HEVCHighTierLevel21);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H90", CodecProfileLevel.HEVCHighTierLevel3);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H93", CodecProfileLevel.HEVCHighTierLevel31);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H120", CodecProfileLevel.HEVCHighTierLevel4);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H123", CodecProfileLevel.HEVCHighTierLevel41);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H150", CodecProfileLevel.HEVCHighTierLevel5);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H153", CodecProfileLevel.HEVCHighTierLevel51);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H156", CodecProfileLevel.HEVCHighTierLevel52);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H180", CodecProfileLevel.HEVCHighTierLevel6);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H183", CodecProfileLevel.HEVCHighTierLevel61);
        HEVC_CODEC_STRING_TO_PROFILE_LEVEL.put("H186", CodecProfileLevel.HEVCHighTierLevel62);
    }
}
