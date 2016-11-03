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

    public static byte[] buildAacLcAudioSpecificConfig(int sampleRate,int numChannels){

    }
}
