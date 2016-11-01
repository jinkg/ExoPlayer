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
