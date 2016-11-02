package com.yalin.exoplayer.source.smoothstreaming.manifest;

import android.net.Uri;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.UriUtil;
import com.yalin.exoplayer.util.Util;

import java.util.List;
import java.util.UUID;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class SsManifest {
    public static final int UNSET_LOOKAHEAD = -1;

    public final int majorVersion;

    public final int minorVersion;

    public final int lookAheadCount;

    public final boolean isLive;

    public final ProtectionElement protectionElement;

    public final StreamElement[] streamElements;

    public final long durationUs;

    public final long dvrWindowLengthUs;

    public SsManifest(int majorVersion, int minorVersion, long timescale, long duration,
                      long dvrWindowLength, int lookAheadCount, boolean isLive, ProtectionElement protectionElement,
                      StreamElement[] streamElements) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.lookAheadCount = lookAheadCount;
        this.isLive = isLive;
        this.protectionElement = protectionElement;
        this.streamElements = streamElements;
        dvrWindowLengthUs = dvrWindowLength == 0 ? C.TIME_UNSET
                : Util.scaleLargeTimestamp(dvrWindowLength, C.MICROS_PER_SECOND, timescale);
        this.durationUs = duration == 0 ? C.TIME_UNSET
                : Util.scaleLargeTimestamp(duration, C.MICROS_PER_SECOND, timescale);
    }

    public static class ProtectionElement {
        public final UUID uuid;
        public final byte[] data;

        public ProtectionElement(UUID uuid, byte[] data) {
            this.uuid = uuid;
            this.data = data;
        }
    }

    public static class StreamElement {
        private static final String URL_PLACEHOLDER_START_TIME = "{start time}";
        private static final String URL_PLACEHOLDER_BITRATE = "{bitrate}";

        public final int type;
        public final String subType;
        public final long timescale;
        public final String name;
        public final int maxWidth;
        public final int maxHeight;
        public final int displayWidth;
        public final int displayHeight;
        public final String language;
        public final Format[] formats;
        public final int chunkCount;

        private final String baseUri;
        private final String chunkTemplate;

        private final List<Long> chunkStartTimes;
        private final long[] chunkStartTimesUs;
        private final long lastChunkDurationUs;

        public StreamElement(String baseUri, String chunkTemplate, int type, String subType,
                             long timescale, String name, int maxWidth, int maxHeight, int displayWidth,
                             int displayHeight, String language, Format[] formats, List<Long> chunkStartTimes,
                             long lastChunkDuration) {
            this.baseUri = baseUri;
            this.chunkTemplate = chunkTemplate;
            this.type = type;
            this.subType = subType;
            this.timescale = timescale;
            this.name = name;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.language = language;
            this.formats = formats;
            this.chunkCount = chunkStartTimes.size();
            this.chunkStartTimes = chunkStartTimes;
            lastChunkDurationUs =
                    Util.scaleLargeTimestamp(lastChunkDuration, C.MICROS_PER_SECOND, timescale);
            chunkStartTimesUs =
                    Util.scaleLargeTimestamps(chunkStartTimes, C.MICROS_PER_SECOND, timescale);
        }

        public int getChunkIndex(long timeUs) {
            return Util.binarySearchFloor(chunkStartTimesUs, timeUs, true, true);
        }

        public long getStartTimeUs(int chunkIndex) {
            return chunkStartTimesUs[chunkIndex];
        }

        public long getChunkDurationUs(int chunkIndex) {
            return (chunkIndex == chunkCount - 1) ? lastChunkDurationUs
                    : chunkStartTimesUs[chunkIndex + 1] - chunkStartTimesUs[chunkIndex];
        }

        public Uri buildRequestUri(int track, int chunkIndex) {
            Assertions.checkState(formats != null);
            Assertions.checkState(chunkStartTimes != null);
            Assertions.checkState(chunkIndex < chunkStartTimes.size());
            String chunkUrl = chunkTemplate
                    .replace(URL_PLACEHOLDER_BITRATE, Integer.toString(formats[track].bitrate))
                    .replace(URL_PLACEHOLDER_START_TIME, chunkStartTimes.get(chunkIndex).toString());
            return UriUtil.resolveToUri(baseUri, chunkUrl);
        }
    }
}
