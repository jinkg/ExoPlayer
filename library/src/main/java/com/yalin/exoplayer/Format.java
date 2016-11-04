package com.yalin.exoplayer;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.MediaFormat;
import android.os.Parcel;
import android.os.Parcelable;

import com.yalin.exoplayer.drm.DrmInitData;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class Format implements Parcelable {
    public static final int NO_VALUE = -1;

    public static final long OFFSET_SAMPLE_RELATIVE = Long.MAX_VALUE;

    public final String id;

    public final int bitrate;

    public final String codecs;

    public final String containerMimeType;

    public final String sampleMimeType;

    public final int maxInputSize;

    public final List<byte[]> initializationData;

    public final DrmInitData drmInitData;

    public final int width;

    public final int height;

    public final float frameRate;

    public final int rotationDegrees;

    public final float pixelWidthHeightRatio;

    @C.StereoMode
    public final int stereoMode;

    public final byte[] projectionData;

    public final int channelCount;

    public final int sampleRate;

    @C.PcmEncoding
    public final int pcmEncoding;

    public final int encoderDelay;

    public final int encoderPadding;

    public final long subsampleOffsetUs;

    @C.SelectionFlags
    public final int selectionFlags;

    public final String language;

    private int hashCode;
    private MediaFormat frameworkMediaFormat;

    public static Format createVideoContainerFormat(String id, String containerMimeType,
                                                    String sampleMimeType, String codecs, int bitrate, int width, int height,
                                                    float frameRate, List<byte[]> initializationData) {
        return new Format(id, containerMimeType, sampleMimeType, codecs, bitrate, NO_VALUE, width,
                height, frameRate, NO_VALUE, NO_VALUE, null, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, 0, null, OFFSET_SAMPLE_RELATIVE, initializationData, null);
    }

    public static Format createVideoSampleFormat(String id, String sampleMimeType, String codecs,
                                                 int bitrate, int maxInputSize, int width, int height, float frameRate,
                                                 List<byte[]> initializationData, DrmInitData drmInitData) {
        return createVideoSampleFormat(id, sampleMimeType, codecs, bitrate, maxInputSize, width,
                height, frameRate, initializationData, NO_VALUE, NO_VALUE, drmInitData);
    }

    public static Format createVideoSampleFormat(String id, String sampleMimeType, String codecs,
                                                 int bitrate, int maxInputSize, int width, int height, float frameRate,
                                                 List<byte[]> initializationData, int rotationDegrees, float pixelWidthHeightRatio,
                                                 DrmInitData drmInitData) {
        return createVideoSampleFormat(id, sampleMimeType, codecs, bitrate, maxInputSize, width,
                height, frameRate, initializationData, rotationDegrees, pixelWidthHeightRatio, null,
                NO_VALUE, drmInitData);
    }

    public static Format createVideoSampleFormat(String id, String sampleMimeType, String codecs,
                                                 int bitrate, int maxInputSize, int width, int height, float frameRate,
                                                 List<byte[]> initializationData, int rotationDegrees, float pixelWidthHeightRatio,
                                                 byte[] projectionData, @C.StereoMode int stereoMode, DrmInitData drmInitData) {
        return new Format(id, null, sampleMimeType, codecs, bitrate, maxInputSize, width, height,
                frameRate, rotationDegrees, pixelWidthHeightRatio, projectionData, stereoMode, NO_VALUE,
                NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, 0, null, OFFSET_SAMPLE_RELATIVE, initializationData,
                drmInitData);
    }

    public static Format createAudioContainerFormat(String id, String containerMimeType,
                                                    String sampleMimeType, String codecs, int bitrate, int channelCount, int sampleRate,
                                                    List<byte[]> initializationData, @C.SelectionFlags int selectionFlags, String language) {
        return new Format(id, containerMimeType, sampleMimeType, codecs, bitrate, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, null, NO_VALUE, channelCount, sampleRate, NO_VALUE,
                NO_VALUE, NO_VALUE, selectionFlags, language, OFFSET_SAMPLE_RELATIVE, initializationData,
                null);
    }

    public static Format createAudioSampleFormat(String id, String sampleMimeType, String codecs,
                                                 int bitrate, int maxInputSize, int channelCount, int sampleRate,
                                                 List<byte[]> initializationData, DrmInitData drmInitData,
                                                 @C.SelectionFlags int selectionFlags, String language) {
        return createAudioSampleFormat(id, sampleMimeType, codecs, bitrate, maxInputSize, channelCount,
                sampleRate, NO_VALUE, initializationData, drmInitData, selectionFlags, language);
    }

    public static Format createAudioSampleFormat(String id, String sampleMimeType, String codecs,
                                                 int bitrate, int maxInputSize, int channelCount, int sampleRate,
                                                 @C.PcmEncoding int pcmEncoding, List<byte[]> initializationData, DrmInitData drmInitData,
                                                 @C.SelectionFlags int selectionFlags, String language) {
        return createAudioSampleFormat(id, sampleMimeType, codecs, bitrate, maxInputSize, channelCount,
                sampleRate, pcmEncoding, NO_VALUE, NO_VALUE, initializationData, drmInitData,
                selectionFlags, language);
    }

    public static Format createAudioSampleFormat(String id, String sampleMimeType, String codecs,
                                                 int bitrate, int maxInputSize, int channelCount, int sampleRate,
                                                 @C.PcmEncoding int pcmEncoding, int encoderDelay, int encoderPadding,
                                                 List<byte[]> initializationData, DrmInitData drmInitData,
                                                 @C.SelectionFlags int selectionFlags, String language) {
        return new Format(id, null, sampleMimeType, codecs, bitrate, maxInputSize, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, NO_VALUE, null, NO_VALUE, channelCount, sampleRate, pcmEncoding,
                encoderDelay, encoderPadding, selectionFlags, language, OFFSET_SAMPLE_RELATIVE,
                initializationData, drmInitData);
    }

    public static Format createTextContainerFormat(String id, String containerMimeType,
                                                   String sampleMimeType, String codecs, int bitrate, @C.SelectionFlags int selectionFlags,
                                                   String language) {
        return new Format(id, containerMimeType, sampleMimeType, codecs, bitrate, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, null, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, selectionFlags, language, OFFSET_SAMPLE_RELATIVE, null, null);
    }

    public static Format createTextSampleFormat(String id, String sampleMimeType, String codecs,
                                                int bitrate, @C.SelectionFlags int selectionFlags, String language, DrmInitData drmInitData) {
        return createTextSampleFormat(id, sampleMimeType, codecs, bitrate, selectionFlags, language,
                drmInitData, OFFSET_SAMPLE_RELATIVE);
    }

    public static Format createTextSampleFormat(String id, String sampleMimeType, String codecs,
                                                int bitrate, @C.SelectionFlags int selectionFlags, String language, DrmInitData drmInitData,
                                                long subsampleOffsetUs) {
        return new Format(id, null, sampleMimeType, codecs, bitrate, NO_VALUE, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, NO_VALUE, null, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE,
                NO_VALUE, selectionFlags, language, subsampleOffsetUs, null, drmInitData);
    }

    public static Format createContainerFormat(String id, String containerMimeType, String codecs,
                                               String sampleMimeType, int bitrate) {
        return new Format(id, containerMimeType, sampleMimeType, codecs, bitrate, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE, null, NO_VALUE, NO_VALUE, NO_VALUE, NO_VALUE,
                NO_VALUE, NO_VALUE, 0, null, OFFSET_SAMPLE_RELATIVE, null, null);
    }

    public Format(String id, String containerMimeType, String sampleMimeType, String codecs,
                  int bitrate, int maxInputSize, int width, int height, float frameRate, int rotationDegrees,
                  float pixelWidthHeightRatio, byte[] projectionData, @C.StereoMode int stereoMode,
                  int channelCount, int sampleRate, @C.PcmEncoding int pcmEncoding, int encoderDelay,
                  int encoderPadding, @C.SelectionFlags int selectionFlags, String language,
                  long subsampleOffsetUs, List<byte[]> initializationData, DrmInitData drmInitData) {
        this.id = id;
        this.containerMimeType = containerMimeType;
        this.sampleMimeType = sampleMimeType;
        this.codecs = codecs;
        this.bitrate = bitrate;
        this.maxInputSize = maxInputSize;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.rotationDegrees = rotationDegrees;
        this.pixelWidthHeightRatio = pixelWidthHeightRatio;
        this.stereoMode = stereoMode;
        this.projectionData = projectionData;
        this.channelCount = channelCount;
        this.sampleRate = sampleRate;
        this.pcmEncoding = pcmEncoding;
        this.encoderDelay = encoderDelay;
        this.encoderPadding = encoderPadding;
        this.selectionFlags = selectionFlags;
        this.language = language;
        this.subsampleOffsetUs = subsampleOffsetUs;
        this.initializationData = initializationData == null ? Collections.<byte[]>emptyList()
                : initializationData;
        this.drmInitData = drmInitData;
    }

    @SuppressLint("InlinedApi")
    @TargetApi(16)
    public final MediaFormat getFrameworkMediaFormatV16() {
        if (frameworkMediaFormat == null) {
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, sampleMimeType);
            maybeSetStringV16(format, MediaFormat.KEY_LANGUAGE, language);
            maybeSetIntegerV16(format, MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize);
            maybeSetIntegerV16(format, MediaFormat.KEY_WIDTH, width);
            maybeSetIntegerV16(format, MediaFormat.KEY_HEIGHT, height);
            maybeSetFloatV16(format, MediaFormat.KEY_FRAME_RATE, frameRate);
            maybeSetIntegerV16(format, "rotation-degrees", rotationDegrees);
            maybeSetIntegerV16(format, MediaFormat.KEY_CHANNEL_COUNT, channelCount);
            maybeSetIntegerV16(format, MediaFormat.KEY_SAMPLE_RATE, sampleRate);
            maybeSetIntegerV16(format, "encoder-delay", encoderDelay);
            maybeSetIntegerV16(format, "encoder-padding", encoderPadding);
            for (int i = 0; i < initializationData.size(); i++) {
                format.setByteBuffer("csd-" + i, ByteBuffer.wrap(initializationData.get(i)));
            }
            frameworkMediaFormat = format;
        }
        return frameworkMediaFormat;
    }

    @TargetApi(16)
    private static void maybeSetStringV16(MediaFormat format, String key, String value) {
        if (value != null) {
            format.setString(key, value);
        }
    }

    @TargetApi(16)
    private static void maybeSetIntegerV16(MediaFormat format, String key, int value) {
        if (value != NO_VALUE) {
            format.setInteger(key, value);
        }
    }

    @TargetApi(16)
    private static void maybeSetFloatV16(MediaFormat format, String key, float value) {
        if (value != NO_VALUE) {
            format.setFloat(key, value);
        }
    }

    public Format copyWithDrmInitData(DrmInitData drmInitData) {
        return new Format(id, containerMimeType, sampleMimeType, codecs, bitrate, maxInputSize,
                width, height, frameRate, rotationDegrees, pixelWidthHeightRatio, projectionData,
                stereoMode, channelCount, sampleRate, pcmEncoding, encoderDelay, encoderPadding,
                selectionFlags, language, subsampleOffsetUs, initializationData, drmInitData);
    }

    public Format copyWithSubsampleOffsetUs(long subsampleOffsetUs) {
        return new Format(id, containerMimeType, sampleMimeType, codecs, bitrate, maxInputSize,
                width, height, frameRate, rotationDegrees, pixelWidthHeightRatio, projectionData,
                stereoMode, channelCount, sampleRate, pcmEncoding, encoderDelay, encoderPadding,
                selectionFlags, language, subsampleOffsetUs, initializationData, drmInitData);
    }

    public Format copyWithMaxInputSize(int maxInputSize) {
        return new Format(id, containerMimeType, sampleMimeType, codecs, bitrate, maxInputSize,
                width, height, frameRate, rotationDegrees, pixelWidthHeightRatio, projectionData,
                stereoMode, channelCount, sampleRate, pcmEncoding, encoderDelay, encoderPadding,
                selectionFlags, language, subsampleOffsetUs, initializationData, drmInitData);
    }

    public Format copyWithGaplessInfo(int encoderDelay, int encoderPadding) {
        return new Format(id, containerMimeType, sampleMimeType, codecs, bitrate, maxInputSize,
                width, height, frameRate, rotationDegrees, pixelWidthHeightRatio, projectionData,
                stereoMode, channelCount, sampleRate, pcmEncoding, encoderDelay, encoderPadding,
                selectionFlags, language, subsampleOffsetUs, initializationData, drmInitData);
    }

    public int getPixelCount() {
        return width == NO_VALUE || height == NO_VALUE ? NO_VALUE : (width * height);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(containerMimeType);
        dest.writeString(sampleMimeType);
        dest.writeString(codecs);
        dest.writeInt(bitrate);
        dest.writeInt(maxInputSize);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeFloat(frameRate);
        dest.writeInt(rotationDegrees);
        dest.writeFloat(pixelWidthHeightRatio);
        dest.writeInt(projectionData != null ? 1 : 0);
        if (projectionData != null) {
            dest.writeByteArray(projectionData);
        }
        dest.writeInt(stereoMode);
        dest.writeInt(channelCount);
        dest.writeInt(sampleRate);
        dest.writeInt(pcmEncoding);
        dest.writeInt(encoderDelay);
        dest.writeInt(encoderPadding);
        dest.writeInt(selectionFlags);
        dest.writeString(language);
        dest.writeLong(subsampleOffsetUs);
        int initializationDataSize = initializationData.size();
        for (int i = 0; i < initializationDataSize; i++) {
            dest.writeByteArray(initializationData.get(i));
        }
        dest.writeParcelable(drmInitData, 0);
    }

    @SuppressWarnings("ResourceType")
    protected Format(Parcel in) {
        id = in.readString();
        containerMimeType = in.readString();
        sampleMimeType = in.readString();
        codecs = in.readString();
        bitrate = in.readInt();
        maxInputSize = in.readInt();
        width = in.readInt();
        height = in.readInt();
        frameRate = in.readFloat();
        rotationDegrees = in.readInt();
        pixelWidthHeightRatio = in.readFloat();
        boolean hasProjectionData = in.readInt() != 0;
        projectionData = hasProjectionData ? in.createByteArray() : null;
        stereoMode = in.readInt();
        channelCount = in.readInt();
        sampleRate = in.readInt();
        pcmEncoding = in.readInt();
        encoderDelay = in.readInt();
        encoderPadding = in.readInt();
        selectionFlags = in.readInt();
        language = in.readString();
        subsampleOffsetUs = in.readLong();
        int initializationDataSize = in.readInt();
        initializationData = new ArrayList<>(initializationDataSize);
        for (int i = 0; i < initializationDataSize; i++) {
            initializationData.add(in.createByteArray());
        }
        drmInitData = in.readParcelable(DrmInitData.class.getClassLoader());
    }

    public static final Creator<Format> CREATOR = new Creator<Format>() {
        @Override
        public Format createFromParcel(Parcel source) {
            return new Format(source);
        }

        @Override
        public Format[] newArray(int size) {
            return new Format[size];
        }
    };


}
