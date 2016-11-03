package com.yalin.exoplayer.source.smoothstreaming.manifest;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.ParserException;
import com.yalin.exoplayer.drm.DrmInitData;
import com.yalin.exoplayer.drm.DrmInitData.SchemeData;
import com.yalin.exoplayer.extractor.mp4.PsshAtomUtil;
import com.yalin.exoplayer.source.smoothstreaming.manifest.SsManifest.ProtectionElement;
import com.yalin.exoplayer.source.smoothstreaming.manifest.SsManifest.StreamElement;
import com.yalin.exoplayer.upstream.ParsingLoadable;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.CodecSpecificDataUtil;
import com.yalin.exoplayer.util.MimeTypes;
import com.yalin.exoplayer.util.Util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class SsManifestParser implements ParsingLoadable.Parser<SsManifest> {
    private final XmlPullParserFactory xmlPullParserFactory;

    public SsManifestParser() {
        try {
            xmlPullParserFactory = XmlPullParserFactory.newInstance();
        } catch (XmlPullParserException e) {
            throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
        }
    }

    @Override
    public SsManifest parse(Uri uri, InputStream inputStream) throws IOException {
        try {
            XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
            xmlPullParser.setInput(inputStream, null);
            SmoothStreamingMediaParser smoothStreamingMediaParser =
                    new SmoothStreamingMediaParser(null, uri.toString());
            return (SsManifest) smoothStreamingMediaParser.parse(xmlPullParser);
        } catch (XmlPullParserException e) {
            throw new ParserException(e);
        }
    }

    public static class MissingFieldException extends ParserException {
        public MissingFieldException(String fieldName) {
            super("Missing required field: " + fieldName);
        }
    }

    private abstract static class ElementParser {
        private final String baseUri;
        private final String tag;

        private final ElementParser parent;
        private final List<Pair<String, Object>> normalizedAttributes;

        public ElementParser(ElementParser parent, String baseUri, String tag) {
            this.parent = parent;
            this.baseUri = baseUri;
            this.tag = tag;
            this.normalizedAttributes = new LinkedList<>();
        }

        public final Object parse(XmlPullParser xmlParser) throws XmlPullParserException, IOException {
            String tagName;
            boolean foundStartTag = false;
            int skippingElementDepth = 0;
            while (true) {
                int eventType = xmlParser.getEventType();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        tagName = xmlParser.getName();
                        if (tag.equals(tagName)) {
                            foundStartTag = true;
                            parseStartTag(xmlParser);
                        } else if (foundStartTag) {
                            if (skippingElementDepth > 0) {
                                skippingElementDepth++;
                            } else if (handleChildInline(tagName)) {
                                parseStartTag(xmlParser);
                            } else {
                                ElementParser childElementParser = newChildParser(this, tagName, baseUri);
                                if (childElementParser == null) {
                                    skippingElementDepth = 1;
                                } else {
                                    addChild(childElementParser.parse(xmlParser));
                                }
                            }
                        }
                        break;
                    case XmlPullParser.TEXT:
                        if (foundStartTag && skippingElementDepth == 0) {
                            parseText(xmlParser);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if (foundStartTag) {
                            if (skippingElementDepth > 0) {
                                skippingElementDepth--;
                            } else {
                                tagName = xmlParser.getName();
                                parseEndTag(xmlParser);
                                if (!handleChildInline(tagName)) {
                                    return build();
                                }
                            }
                        }
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        return null;
                    default:
                        break;
                }
                xmlParser.next();
            }
        }

        private ElementParser newChildParser(ElementParser parent, String name, String baseUri) {
            if (QualityLevelParser.TAG.equals(name)) {
                return new QualityLevelParser(parent, baseUri);
            } else if (ProtectionParser.TAG.equals(name)) {
                return new ProtectionParser(parent, baseUri);
            } else if (StreamIndexParser.TAG.equals(name)) {
                return new StreamIndexParser(parent, baseUri);
            }
            return null;
        }

        protected final void putNormalizedAttribute(String key, Object value) {
            normalizedAttributes.add(Pair.create(key, value));
        }

        protected final Object getNormalizedAttribute(String key) {
            for (int i = 0; i < normalizedAttributes.size(); i++) {
                Pair<String, Object> pair = normalizedAttributes.get(i);
                if (pair.first.equals(key)) {
                    return pair.second;
                }
            }
            return parent == null ? null : parent.getNormalizedAttribute(key);
        }

        protected boolean handleChildInline(String tagName) {
            return false;
        }

        protected void parseStartTag(XmlPullParser parser) throws ParserException {

        }

        protected void parseText(XmlPullParser parser) {

        }

        protected void parseEndTag(XmlPullParser parser) {

        }

        protected void addChild(Object child) {

        }

        protected abstract Object build();

        protected final String parseRequiredString(XmlPullParser parser, String key)
                throws MissingFieldException {
            String value = parser.getAttributeValue(null, key);
            if (value != null) {
                return value;
            } else {
                throw new MissingFieldException(key);
            }
        }

        protected final int parseInt(XmlPullParser parser, String key, int defaultValue)
                throws ParserException {
            String value = parser.getAttributeValue(null, key);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ParserException(e);
                }
            } else {
                return defaultValue;
            }
        }

        protected final int parseRequiredInt(XmlPullParser parser, String key)
                throws ParserException {
            String value = parser.getAttributeValue(null, key);
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ParserException(e);
                }
            } else {
                throw new MissingFieldException(key);
            }
        }

        protected final long parseLong(XmlPullParser parser, String key, long defaultValue)
                throws ParserException {
            String value = parser.getAttributeValue(null, key);
            if (value != null) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new ParserException(e);
                }
            } else {
                return defaultValue;
            }
        }

        protected final long parseRequiredLong(XmlPullParser parser, String key)
                throws ParserException {
            String value = parser.getAttributeValue(null, key);
            if (value != null) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new ParserException(e);
                }
            } else {
                throw new MissingFieldException(key);
            }
        }

        protected final boolean parseBoolean(XmlPullParser parser, String key, boolean defaultValue)
                throws ParserException {
            String value = parser.getAttributeValue(null, key);
            if (value != null) {
                return Boolean.parseBoolean(value);
            } else {
                return defaultValue;
            }
        }
    }

    private static class SmoothStreamingMediaParser extends ElementParser {
        public static final String TAG = "SmoothStreamingMedia";

        private static final String KEY_MAJOR_VERSION = "MajorVersion";
        private static final String KEY_MINOR_VERSION = "MinorVersion";
        private static final String KEY_TIME_SCALE = "TimeScale";
        private static final String KEY_DVR_WINDOW_LENGTH = "DVRWindowLength";
        private static final String KEY_DURATION = "Duration";
        private static final String KEY_LOOKAHEAD_COUNT = "LookaheadCount";
        private static final String KEY_IS_LIVE = "IsLive";

        private final List<StreamElement> streamElements;

        private int majorVersion;
        private int minorVersion;
        private long timescale;
        private long duration;
        private long dvrWindowLength;
        private int lookAheadCount;
        private boolean isLive;
        private ProtectionElement protectionElement;

        public SmoothStreamingMediaParser(ElementParser parent, String baseUri) {
            super(parent, baseUri, TAG);
            lookAheadCount = SsManifest.UNSET_LOOKAHEAD;
            protectionElement = null;
            streamElements = new LinkedList<>();
        }

        @Override
        protected void parseStartTag(XmlPullParser parser) throws ParserException {
            majorVersion = parseRequiredInt(parser, KEY_MAJOR_VERSION);
            minorVersion = parseRequiredInt(parser, KEY_MINOR_VERSION);
            timescale = parseLong(parser, KEY_TIME_SCALE, 10000000L);
            duration = parseRequiredLong(parser, KEY_DURATION);
            dvrWindowLength = parseLong(parser, KEY_DVR_WINDOW_LENGTH, 0);
            lookAheadCount = parseInt(parser, KEY_LOOKAHEAD_COUNT, SsManifest.UNSET_LOOKAHEAD);
            isLive = parseBoolean(parser, KEY_IS_LIVE, false);
            putNormalizedAttribute(KEY_TIME_SCALE, timescale);
        }

        @Override
        protected void addChild(Object child) {
            if (child instanceof StreamElement) {
                streamElements.add((StreamElement) child);
            } else if (child instanceof ProtectionElement) {
                Assertions.checkState(protectionElement == null);
                protectionElement = (ProtectionElement) child;
            }
        }

        @Override
        protected Object build() {
            StreamElement[] streamElementArray = new StreamElement[streamElements.size()];
            streamElements.toArray(streamElementArray);
            if (protectionElement != null) {
                DrmInitData drmInitData = new DrmInitData(new SchemeData(protectionElement.uuid,
                        MimeTypes.VIDEO_MP4, protectionElement.data));
                for (StreamElement streamElement : streamElementArray) {
                    for (int i = 0; i < streamElement.formats.length; i++) {
                        streamElement.formats[i] = streamElement.formats[i].copyWithDrmInitData(drmInitData);
                    }
                }
            }
            return new SsManifest(majorVersion, minorVersion, timescale, duration, dvrWindowLength,
                    lookAheadCount, isLive, protectionElement, streamElementArray);
        }
    }

    private static class StreamIndexParser extends ElementParser {
        public static final String TAG = "StreamIndex";
        private static final String TAG_STREAM_FRAGMENT = "c";

        private static final String KEY_TYPE = "Type";
        private static final String KEY_TYPE_AUDIO = "audio";
        private static final String KEY_TYPE_VIDEO = "video";
        private static final String KEY_TYPE_TEXT = "text";
        private static final String KEY_SUB_TYPE = "Subtype";
        private static final String KEY_NAME = "Name";
        private static final String KEY_URL = "Url";
        private static final String KEY_MAX_WIDTH = "MaxWidth";
        private static final String KEY_MAX_HEIGHT = "MaxHeight";
        private static final String KEY_DISPLAY_WIDTH = "DisplayWidth";
        private static final String KEY_DISPLAY_HEIGHT = "DisplayHeight";
        private static final String KEY_LANGUAGE = "Language";
        private static final String KEY_TIME_SCALE = "TimeScale";

        private static final String KEY_FRAGMENT_DURATION = "d";
        private static final String KEY_FRAGMENT_START_TIME = "t";
        private static final String KEY_FRAGMENT_REPEAT_COUNT = "r";

        private final String baseUri;
        private final List<Format> formats;

        private int type;
        private String subType;
        private long timescale;
        private String name;
        private String url;
        private int maxWidth;
        private int maxHeight;
        private int displayWidth;
        private int displayHeight;
        private String language;
        private ArrayList<Long> startTimes;

        private long lastChunkDuration;

        public StreamIndexParser(ElementParser parent, String baseUri) {
            super(parent, baseUri, TAG);
            this.baseUri = baseUri;
            formats = new LinkedList<>();
        }

        @Override
        protected boolean handleChildInline(String tagName) {
            return TAG_STREAM_FRAGMENT.equals(tagName);
        }

        @Override
        protected void parseStartTag(XmlPullParser parser) throws ParserException {
            if (TAG_STREAM_FRAGMENT.equals(parser.getName())) {
                parseStreamFragmentStartTag(parser);
            } else {
                parseStreamElementStartTag(parser);
            }
        }

        private void parseStreamFragmentStartTag(XmlPullParser parser) throws ParserException {
            int chunkIndex = startTimes.size();
            long startTime = parseLong(parser, KEY_FRAGMENT_START_TIME, C.TIME_UNSET);
            if (startTime == C.TIME_UNSET) {
                if (chunkIndex == 0) {
                    startTime = 0;
                } else if (lastChunkDuration != C.INDEX_UNSET) {
                    startTime = startTimes.get(chunkIndex - 1) + lastChunkDuration;
                } else {
                    throw new ParserException("Unable to infer start time");
                }
            }
            chunkIndex++;
            startTimes.add(startTime);
            lastChunkDuration = parseLong(parser, KEY_FRAGMENT_DURATION, C.TIME_UNSET);
            long repeatCount = parseLong(parser, KEY_FRAGMENT_REPEAT_COUNT, 1L);
            if (repeatCount > 1 && lastChunkDuration == C.TIME_UNSET) {
                throw new ParserException("Repeated chunk with unspecified duration");
            }
            for (int i = 1; i < repeatCount; i++) {
                chunkIndex++;
                startTimes.add(startTime + (lastChunkDuration * i));
            }
        }

        private void parseStreamElementStartTag(XmlPullParser parser) throws ParserException {
            type = parseType(parser);
            putNormalizedAttribute(KEY_TYPE, type);
            if (type == C.TRACK_TYPE_TEXT) {
                subType = parseRequiredString(parser, KEY_SUB_TYPE);
            } else {
                subType = parser.getAttributeValue(null, KEY_SUB_TYPE);
            }
            name = parser.getAttributeValue(null, KEY_NAME);
            url = parseRequiredString(parser, KEY_URL);
            maxWidth = parseInt(parser, KEY_MAX_WIDTH, Format.NO_VALUE);
            maxHeight = parseInt(parser, KEY_MAX_HEIGHT, Format.NO_VALUE);
            displayWidth = parseInt(parser, KEY_DISPLAY_WIDTH, Format.NO_VALUE);
            displayHeight = parseInt(parser, KEY_DISPLAY_HEIGHT, Format.NO_VALUE);
            language = parser.getAttributeValue(null, KEY_LANGUAGE);
            putNormalizedAttribute(KEY_LANGUAGE, language);
            timescale = parseInt(parser, KEY_TIME_SCALE, -1);
            if (timescale == -1) {
                timescale = (long) getNormalizedAttribute(KEY_TIME_SCALE);
            }
            startTimes = new ArrayList<>();
        }

        private int parseType(XmlPullParser parser) throws ParserException {
            String value = parser.getAttributeValue(null, KEY_TYPE);
            if (value != null) {
                if (KEY_TYPE_AUDIO.equalsIgnoreCase(value)) {
                    return C.TRACK_TYPE_AUDIO;
                } else if (KEY_TYPE_VIDEO.equalsIgnoreCase(value)) {
                    return C.TRACK_TYPE_VIDEO;
                } else if (KEY_TYPE_TEXT.equalsIgnoreCase(value)) {
                    return C.TRACK_TYPE_TEXT;
                } else {
                    throw new ParserException("Invalid key value[" + value + "]");
                }
            }
            throw new MissingFieldException(KEY_TYPE);
        }

        @Override
        protected void addChild(Object child) {
            if (child instanceof Format) {
                formats.add((Format) child);
            }
        }

        @Override
        protected Object build() {
            Format[] formatArray = new Format[formats.size()];
            formats.toArray(formatArray);
            return new StreamElement(baseUri, url, type, subType, timescale, name, maxWidth, maxHeight,
                    displayWidth, displayHeight, language, formatArray, startTimes, lastChunkDuration);
        }
    }

    private static class ProtectionParser extends ElementParser {

        public static final String TAG = "Protection";
        public static final String TAG_PROTECTION_HEADER = "ProtectionHeader";

        public static final String KEY_SYSTEM_ID = "SystemID";

        private boolean inProjectionHeader;
        private UUID uuid;
        private byte[] initData;

        public ProtectionParser(ElementParser parent, String baseUri) {
            super(parent, baseUri, TAG);
        }

        @Override
        protected boolean handleChildInline(String tagName) {
            return TAG_PROTECTION_HEADER.equals(tagName);
        }

        @Override
        protected void parseStartTag(XmlPullParser parser) throws ParserException {
            if (TAG_PROTECTION_HEADER.equals(parser.getName())) {
                inProjectionHeader = true;
                String uuidString = parser.getAttributeValue(null, KEY_SYSTEM_ID);
                uuidString = stripCurlyBraces(uuidString);
                uuid = UUID.fromString(uuidString);
            }
        }

        @Override
        protected void parseText(XmlPullParser parser) {
            if (inProjectionHeader) {
                initData = Base64.decode(parser.getText(), Base64.DEFAULT);
            }
        }

        @Override
        protected void parseEndTag(XmlPullParser parser) {
            if (TAG_PROTECTION_HEADER.equals(parser.getName())) {
                inProjectionHeader = false;
            }
        }

        @Override
        protected Object build() {
            return new ProtectionElement(uuid, PsshAtomUtil.buildPsshAtom(uuid, initData));
        }

        private static String stripCurlyBraces(String uuidString) {
            if (uuidString.charAt(0) == '{' && uuidString.charAt(uuidString.length() - 1) == '}') {
                uuidString = uuidString.substring(1, uuidString.length() - 1);
            }
            return uuidString;
        }
    }

    private static class QualityLevelParser extends ElementParser {
        public static final String TAG = "QualityLevel";

        private static final String KEY_INDEX = "Index";
        private static final String KEY_BITRATE = "Bitrate";
        private static final String KEY_CODEC_PRIVATE_DATA = "CodecPrivateData";
        private static final String KEY_SAMPLING_RATE = "SamplingRate";
        private static final String KEY_CHANNELS = "Channels";
        private static final String KEY_FOUR_CC = "FourCC";
        private static final String KEY_TYPE = "type";
        private static final String KEY_LANGUAGE = "Language";
        private static final String KEY_MAX_WIDTH = "MaxWidth";
        private static final String KEY_MAX_HEIGHT = "MaxHeight";

        private Format format;

        public QualityLevelParser(ElementParser parent, String baseUri) {
            super(parent, baseUri, TAG);
        }

        @Override
        protected void parseStartTag(XmlPullParser parser) throws ParserException {
            int type = (int) getNormalizedAttribute(KEY_TYPE);
            String id = parser.getAttributeValue(null, KEY_INDEX);
            int bitrate = parseRequiredInt(parser, KEY_BITRATE);
            String sampleMimeType = fourCCToMimeType(parseRequiredString(parser, KEY_FOUR_CC));

            if (type == C.TRACK_TYPE_VIDEO) {
                int width = parseRequiredInt(parser, KEY_MAX_WIDTH);
                int height = parseRequiredInt(parser, KEY_MAX_HEIGHT);
                List<byte[]> codecSpecificData = buildCodecSpecificData(
                        parser.getAttributeValue(null, KEY_CODEC_PRIVATE_DATA));
                format = Format.createVideoContainerFormat(id, MimeTypes.VIDEO_MP4, sampleMimeType, null,
                        bitrate, width, height, Format.NO_VALUE, codecSpecificData);
            } else if (type == C.TRACK_TYPE_AUDIO) {
                sampleMimeType = sampleMimeType == null ? MimeTypes.AUDIO_AAC : sampleMimeType;
                int channels = parseRequiredInt(parser, KEY_CHANNELS);
                int samplingRate = parseRequiredInt(parser, KEY_SAMPLING_RATE);
                List<byte[]> codecSpecificData = buildCodecSpecificData(
                        parser.getAttributeValue(null, KEY_CODEC_PRIVATE_DATA));
                if (codecSpecificData.isEmpty() && MimeTypes.AUDIO_AAC.equalsIgnoreCase(sampleMimeType)) {
                    codecSpecificData = Collections.singletonList(
                            CodecSpecificDataUtil.buildAacLcAudioSpecificConfig(samplingRate, channels));
                }
                String language = (String) getNormalizedAttribute(KEY_LANGUAGE);
                format = Format.createAudioContainerFormat(id, MimeTypes.AUDIO_MP4, sampleMimeType, null,
                        bitrate, channels, samplingRate, codecSpecificData, 0, language);
            } else if (type == C.TRACK_TYPE_TEXT) {
                String language = (String) getNormalizedAttribute(KEY_LANGUAGE);
                format = Format.createTextContainerFormat(id, MimeTypes.APPLICATION_MP4, sampleMimeType,
                        null, bitrate, 0, language);
            } else {
                format = Format.createContainerFormat(id, MimeTypes.APPLICATION_MP4, null, sampleMimeType,
                        bitrate);
            }
        }

        @Override
        protected Object build() {
            return format;
        }

        private static List<byte[]> buildCodecSpecificData(String codecSpecificDataString) {
            ArrayList<byte[]> csd = new ArrayList<>();
            if (!TextUtils.isEmpty(codecSpecificDataString)) {
                byte[] codecPrivateData = Util.getBytesFromHexString(codecSpecificDataString);
                byte[][] split = CodecSpecificDataUtil.splitNalUnits(codecPrivateData);
                if (split == null) {
                    csd.add(codecPrivateData);
                } else {
                    Collections.addAll(csd, split);
                }
            }
            return csd;
        }

        private static String fourCCToMimeType(String fourCC) {
            if (fourCC.equalsIgnoreCase("H264") || fourCC.equalsIgnoreCase("X264")
                    || fourCC.equalsIgnoreCase("AVC1") || fourCC.equalsIgnoreCase("DAVC")) {
                return MimeTypes.VIDEO_H264;
            } else if (fourCC.equalsIgnoreCase("AAC") || fourCC.equalsIgnoreCase("AACL")
                    || fourCC.equalsIgnoreCase("AACH") || fourCC.equalsIgnoreCase("AACP")) {
                return MimeTypes.AUDIO_AAC;
            } else if (fourCC.equalsIgnoreCase("TTML")) {
                return MimeTypes.APPLICATION_TTML;
            } else if (fourCC.equalsIgnoreCase("ac-3") || fourCC.equalsIgnoreCase("dac3")) {
                return MimeTypes.AUDIO_AC3;
            } else if (fourCC.equalsIgnoreCase("ec-3") || fourCC.equalsIgnoreCase("dec3")) {
                return MimeTypes.AUDIO_E_AC3;
            } else if (fourCC.equalsIgnoreCase("dtsc")) {
                return MimeTypes.AUDIO_DTS;
            } else if (fourCC.equalsIgnoreCase("dtsh") || fourCC.equalsIgnoreCase("dtsl")) {
                return MimeTypes.AUDIO_DTS_HD;
            } else if (fourCC.equalsIgnoreCase("dtse")) {
                return MimeTypes.AUDIO_DTS_EXPRESS;
            } else if (fourCC.equalsIgnoreCase("opus")) {
                return MimeTypes.AUDIO_OPUS;
            }
            return null;
        }
    }
}
