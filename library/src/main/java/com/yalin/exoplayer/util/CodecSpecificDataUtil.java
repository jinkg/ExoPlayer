package com.yalin.exoplayer.util;

import com.yalin.exoplayer.C;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class CodecSpecificDataUtil {
    private static final byte[] NAL_START_CODE = new byte[]{0, 0, 0, 1};

    private static final int[] AUDIO_SEPCIFIC_CONFIG_SAMPLING_RATE_TABLE = new int[]{
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
    };

    private static final int AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID = -1;

    private static final int[] AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE = new int[]{
            0,
            1, /* mono: <FC> */
            2, /* stereo: (FL, FR) */
            3, /* 3.0: <FC>, (FL, FR) */
            4, /* 4.0: <FC>, (FL, FR), <BC> */
            5, /* 5.0 back: <FC>, (FL, FR), (SL, SR) */
            6, /* 5.1 back: <FC>, (FL, FR), (SL, SR), <BC>, [LFE] */
            8, /* 7.1 wide back: <FC>, (FCL, FCR), (FL, FR), (SL, SR), [LFE] */
            AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
            AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
            AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
            7, /* 6.1: <FC>, (FL, FR), (SL, SR), <RC>, [LFE] */
            8, /* 7.1: <FC>, (FL, FR), (SL, SR), (BL, BR), [LFE] */
            AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID,
            8, /* 7.1 top: <FC>, (FL, FR), (SL, SR), [LFE], (FTL, FTR) */
            AUDIO_SPECIFIC_CONFIG_CHANNEL_CONFIGURATION_INVALID
    };

    private static final int AUDIO_OBJECT_TYPE_AAC_LC = 2;

    public static byte[][] splitNalUnits(byte[] data) {
        if (!isNalStartCode(data, 0)) {
            return null;
        }
        List<Integer> starts = new ArrayList<>();
        int nalUnitIndex = 0;
        do {
            starts.add(nalUnitIndex);
            nalUnitIndex = findNalStartCode(data, nalUnitIndex + NAL_START_CODE.length);
        } while (nalUnitIndex != C.INDEX_UNSET);
        byte[][] split = new byte[starts.size()][];
        for (int i = 0; i < starts.size(); i++) {
            int startIndex = starts.get(i);
            int endIndex = i < starts.size() - 1 ? starts.get(i + 1) : data.length;
            byte[] nal = new byte[endIndex - startIndex];
            System.arraycopy(data, startIndex, nal, 0, nal.length);
            split[i] = nal;
        }
        return split;
    }

    private static int findNalStartCode(byte[] data, int index) {
        int endIndex = data.length - NAL_START_CODE.length;
        for (int i = index; i <= endIndex; i++) {
            if (isNalStartCode(data, i)) {
                return i;
            }
        }
        return C.INDEX_UNSET;
    }

    private static boolean isNalStartCode(byte[] data, int index) {
        if (data.length - index <= NAL_START_CODE.length) {
            return false;
        }
        for (int j = 0; j < NAL_START_CODE.length; j++) {
            if (data[index + j] != NAL_START_CODE[j]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] buildAacLcAudioSpecificConfig(int sampleRate, int numChannels) {
        int sampleRateIndex = C.INDEX_UNSET;
        for (int i = 0; i < AUDIO_SEPCIFIC_CONFIG_SAMPLING_RATE_TABLE.length; ++i) {
            if (sampleRate == AUDIO_SEPCIFIC_CONFIG_SAMPLING_RATE_TABLE[i]) {
                sampleRateIndex = i;
            }
        }
        int channelConfig = C.INDEX_UNSET;
        for (int i = 0; i < AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE.length; ++i) {
            if (numChannels == AUDIO_SPECIFIC_CONFIG_CHANNEL_COUNT_TABLE[i]) {
                channelConfig = i;
            }
        }

        if (sampleRate == C.INDEX_UNSET || channelConfig == C.INDEX_UNSET) {
            throw new IllegalArgumentException("Invalid sample rate or number of channels: " +
                    sampleRate + ", " + numChannels);
        }
        return buildAacAudioSpecificConfig(AUDIO_OBJECT_TYPE_AAC_LC, sampleRateIndex, channelConfig);
    }

    public static byte[] buildAacAudioSpecificConfig(int audioObjectType, int sampleRateIndex,
                                                     int channelConfig) {
        byte[] specificConfig = new byte[2];
        specificConfig[0] = (byte) (((audioObjectType << 3) & 0xF8) | ((sampleRateIndex >> 1) & 0x07));
        specificConfig[1] = (byte) (((sampleRateIndex << 7) & 0x80) | ((channelConfig << 3) & 0x78));
        return specificConfig;
    }
}
