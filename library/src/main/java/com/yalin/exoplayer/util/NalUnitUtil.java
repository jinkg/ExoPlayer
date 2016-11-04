package com.yalin.exoplayer.util;

import android.util.Log;

import java.nio.ByteBuffer;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public class NalUnitUtil {

    private static final String TAG = "NalUnitUtil";

    public static final class SpsData {

        public final int seqParameterSetId;
        public final int width;
        public final int height;
        public final float pixelWidthAspectRatio;
        public final boolean separateColorPlaneFlag;
        public final boolean frameMbsOnlyFlag;
        public final int frameNumLength;
        public final int picOrderCountType;
        public final int picOrderCntLsbLength;
        public final boolean deltaPicOrderAlwaysZeroFlag;

        public SpsData(int seqParameterSetId, int width, int height, float pixelWidthAspectRatio,
                       boolean separateColorPlaneFlag, boolean frameMbsOnlyFlag, int frameNumLength,
                       int picOrderCountType, int picOrderCntLsbLength, boolean deltaPicOrderAlwaysZeroFlag) {
            this.seqParameterSetId = seqParameterSetId;
            this.width = width;
            this.height = height;
            this.pixelWidthAspectRatio = pixelWidthAspectRatio;
            this.separateColorPlaneFlag = separateColorPlaneFlag;
            this.frameMbsOnlyFlag = frameMbsOnlyFlag;
            this.frameNumLength = frameNumLength;
            this.picOrderCountType = picOrderCountType;
            this.picOrderCntLsbLength = picOrderCntLsbLength;
            this.deltaPicOrderAlwaysZeroFlag = deltaPicOrderAlwaysZeroFlag;
        }

    }

    public static final byte[] NAL_START_CODE = new byte[]{0, 0, 0, 1};

    private static final int NAL_UNIT_TYPE_SPS = 7;

    public static final int EXTENDED_SAR = 0xFF;
    /**
     * Aspect ratios indexed by aspect_ratio_idc, in H.264 and H.265 SPSs.
     */
    public static final float[] ASPECT_RATIO_IDC_VALUES = new float[]{
            1f /* Unspecified. Assume square */,
            1f,
            12f / 11f,
            10f / 11f,
            16f / 11f,
            40f / 33f,
            24f / 11f,
            20f / 11f,
            32f / 11f,
            80f / 33f,
            18f / 11f,
            15f / 11f,
            64f / 33f,
            160f / 99f,
            4f / 3f,
            3f / 2f,
            2f
    };

    public static void discardToSps(ByteBuffer data) {
        int length = data.position();
        int consecutiveZeros = 0;
        int offset = 0;
        while (offset + 1 < length) {
            int value = data.get(offset) & 0xFF;
            if (consecutiveZeros == 3) {
                if (value == 1 && (data.get(offset + 1) & 0x1F) == NAL_UNIT_TYPE_SPS) {
                    ByteBuffer offsetData = data.duplicate();
                    offsetData.position(offset - 3);
                    offsetData.limit(length);
                    data.position(0);
                    data.put(offsetData);
                    return;
                }
            } else if (value == 0) {
                consecutiveZeros++;
            }
            if (value != 0) {
                consecutiveZeros = 0;
            }
            offset++;
        }
        data.clear();
    }

    public static SpsData parseSpsNalUnit(byte[] nalData, int nalOffset, int nalLimit) {
        ParsableNalUnitBitArray data = new ParsableNalUnitBitArray(nalData, nalOffset, nalLimit);
        data.skipBits(8); // nal_unit
        int profileIdc = data.readBits(8);
        data.skipBits(16); // constraint bits (6), reserved (2) and level_idc (8)
        int seqParameterSetId = data.readUnsignedExpGolombCodedInt();

        int chromaFormatIdc = 1; // Default is 4:2:0
        boolean separateColorPlaneFlag = false;
        if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
                || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
                || profileIdc == 128 || profileIdc == 138) {
            chromaFormatIdc = data.readUnsignedExpGolombCodedInt();
            if (chromaFormatIdc == 3) {
                separateColorPlaneFlag = data.readBit();
            }
            data.readUnsignedExpGolombCodedInt(); // bit_depth_luma_minus8
            data.readUnsignedExpGolombCodedInt(); // bit_depth_chroma_minus8
            data.skipBits(1); // qpprime_y_zero_transform_bypass_flag
            boolean seqScalingMatrixPresentFlag = data.readBit();
            if (seqScalingMatrixPresentFlag) {
                int limit = (chromaFormatIdc != 3) ? 8 : 12;
                for (int i = 0; i < limit; i++) {
                    boolean seqScalingListPresentFlag = data.readBit();
                    if (seqScalingListPresentFlag) {
                        skipScalingList(data, i < 6 ? 16 : 64);
                    }
                }
            }
        }

        int frameNumLength = data.readUnsignedExpGolombCodedInt() + 4; // log2_max_frame_num_minus4 + 4
        int picOrderCntType = data.readUnsignedExpGolombCodedInt();
        int picOrderCntLsbLength = 0;
        boolean deltaPicOrderAlwaysZeroFlag = false;
        if (picOrderCntType == 0) {
            // log2_max_pic_order_cnt_lsb_minus4 + 4
            picOrderCntLsbLength = data.readUnsignedExpGolombCodedInt() + 4;
        } else if (picOrderCntType == 1) {
            deltaPicOrderAlwaysZeroFlag = data.readBit(); // delta_pic_order_always_zero_flag
            data.readSignedExpGolombCodedInt(); // offset_for_non_ref_pic
            data.readSignedExpGolombCodedInt(); // offset_for_top_to_bottom_field
            long numRefFramesInPicOrderCntCycle = data.readUnsignedExpGolombCodedInt();
            for (int i = 0; i < numRefFramesInPicOrderCntCycle; i++) {
                data.readUnsignedExpGolombCodedInt(); // offset_for_ref_frame[i]
            }
        }
        data.readUnsignedExpGolombCodedInt(); // max_num_ref_frames
        data.skipBits(1); // gaps_in_frame_num_value_allowed_flag

        int picWidthInMbs = data.readUnsignedExpGolombCodedInt() + 1;
        int picHeightInMapUnits = data.readUnsignedExpGolombCodedInt() + 1;
        boolean frameMbsOnlyFlag = data.readBit();
        int frameHeightInMbs = (2 - (frameMbsOnlyFlag ? 1 : 0)) * picHeightInMapUnits;
        if (!frameMbsOnlyFlag) {
            data.skipBits(1); // mb_adaptive_frame_field_flag
        }

        data.skipBits(1); // direct_8x8_inference_flag
        int frameWidth = picWidthInMbs * 16;
        int frameHeight = frameHeightInMbs * 16;
        boolean frameCroppingFlag = data.readBit();
        if (frameCroppingFlag) {
            int frameCropLeftOffset = data.readUnsignedExpGolombCodedInt();
            int frameCropRightOffset = data.readUnsignedExpGolombCodedInt();
            int frameCropTopOffset = data.readUnsignedExpGolombCodedInt();
            int frameCropBottomOffset = data.readUnsignedExpGolombCodedInt();
            int cropUnitX, cropUnitY;
            if (chromaFormatIdc == 0) {
                cropUnitX = 1;
                cropUnitY = 2 - (frameMbsOnlyFlag ? 1 : 0);
            } else {
                int subWidthC = (chromaFormatIdc == 3) ? 1 : 2;
                int subHeightC = (chromaFormatIdc == 1) ? 2 : 1;
                cropUnitX = subWidthC;
                cropUnitY = subHeightC * (2 - (frameMbsOnlyFlag ? 1 : 0));
            }
            frameWidth -= (frameCropLeftOffset + frameCropRightOffset) * cropUnitX;
            frameHeight -= (frameCropTopOffset + frameCropBottomOffset) * cropUnitY;
        }

        float pixelWidthHeightRatio = 1;
        boolean vuiParametersPresentFlag = data.readBit();
        if (vuiParametersPresentFlag) {
            boolean aspectRatioInfoPresentFlag = data.readBit();
            if (aspectRatioInfoPresentFlag) {
                int aspectRatioIdc = data.readBits(8);
                if (aspectRatioIdc == NalUnitUtil.EXTENDED_SAR) {
                    int sarWidth = data.readBits(16);
                    int sarHeight = data.readBits(16);
                    if (sarWidth != 0 && sarHeight != 0) {
                        pixelWidthHeightRatio = (float) sarWidth / sarHeight;
                    }
                } else if (aspectRatioIdc < NalUnitUtil.ASPECT_RATIO_IDC_VALUES.length) {
                    pixelWidthHeightRatio = NalUnitUtil.ASPECT_RATIO_IDC_VALUES[aspectRatioIdc];
                } else {
                    Log.w(TAG, "Unexpected aspect_ratio_idc value: " + aspectRatioIdc);
                }
            }
        }

        return new SpsData(seqParameterSetId, frameWidth, frameHeight, pixelWidthHeightRatio,
                separateColorPlaneFlag, frameMbsOnlyFlag, frameNumLength, picOrderCntType,
                picOrderCntLsbLength, deltaPicOrderAlwaysZeroFlag);
    }

    private static void skipScalingList(ParsableNalUnitBitArray bitArray, int size) {
        int lastScale = 8;
        int nextScale = 8;
        for (int i = 0; i < size; i++) {
            if (nextScale != 0) {
                int deltaScale = bitArray.readSignedExpGolombCodedInt();
                nextScale = (lastScale + deltaScale + 256) % 256;
            }
            lastScale = (nextScale == 0) ? lastScale : nextScale;
        }
    }

}
