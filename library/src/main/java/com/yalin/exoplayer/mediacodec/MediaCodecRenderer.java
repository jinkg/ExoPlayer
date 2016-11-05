package com.yalin.exoplayer.mediacodec;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodec.CryptoException;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.yalin.exoplayer.BaseRenderer;
import com.yalin.exoplayer.C;
import com.yalin.exoplayer.ExoPlaybackException;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.FormatHolder;
import com.yalin.exoplayer.decoder.DecoderCounters;
import com.yalin.exoplayer.decoder.DecoderInputBuffer;
import com.yalin.exoplayer.drm.DrmSession;
import com.yalin.exoplayer.drm.DrmSessionManager;
import com.yalin.exoplayer.drm.FrameworkMediaCrypto;
import com.yalin.exoplayer.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.NalUnitUtil;
import com.yalin.exoplayer.util.TraceUtil;
import com.yalin.exoplayer.util.Util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */
@TargetApi(16)
public abstract class MediaCodecRenderer extends BaseRenderer {

    public static class DecoderInitializationException extends Exception {
        private static final int CUSTOM_ERROR_CODE_BASE = -50000;
        private static final int NO_SUITABLE_DECODER_ERROR = CUSTOM_ERROR_CODE_BASE + 1;
        private static final int DECODER_QUERY_ERROR = CUSTOM_ERROR_CODE_BASE + 2;

        public final String mimeType;

        public final boolean secureDecoderRequired;

        public final String decoderName;

        public final String diagnosticInfo;

        public DecoderInitializationException(Format format, Throwable cause,
                                              boolean secureDecoderRequired, int errorCode) {
            super("Decoder init failed: [" + errorCode + "], " + format, cause);
            this.mimeType = format.sampleMimeType;
            this.secureDecoderRequired = secureDecoderRequired;
            this.decoderName = null;
            this.diagnosticInfo = buildCustomDiagnosticInfo(errorCode);
        }

        public DecoderInitializationException(Format format, Throwable cause,
                                              boolean secureDecoderRequired, String decoderName) {
            super("Decoder init failed: " + decoderName + ", " + format, cause);
            this.mimeType = format.sampleMimeType;
            this.secureDecoderRequired = secureDecoderRequired;
            this.decoderName = decoderName;
            this.diagnosticInfo = Util.SDK_INT >= 21 ? getDiagnosticInfoV21(cause) : null;
        }

        @TargetApi(21)
        private static String getDiagnosticInfoV21(Throwable cause) {
            if (cause instanceof CodecException) {
                return ((CodecException) cause).getDiagnosticInfo();
            }
            return null;
        }

        private static String buildCustomDiagnosticInfo(int errorCode) {
            String sign = errorCode < 0 ? "neg_" : "";
            return "com.yalin.exoplayer.MediaCodecTrackRenderer_" + sign + Math.abs(errorCode);
        }
    }

    private static final String TAG = "MediaCodecRenderer";

    private static final long MAX_CODEC_HOTSWAP_TIME_MS = 1000;

    private static final int RECONFIGURATION_STATE_NONE = 0;

    private static final int RECONFIGURATION_STATE_WRITE_RENDING = 1;

    private static final int RECONFIGURATION_STATE_QUEUE_PENDING = 2;

    private static final int REINITIALIZATION_STATE_NONE = 0;

    private static final int REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM = 1;

    private static final int REINITIALIZATION_STATE_WAIT_END_OF_STREAM = 2;

    private static final byte[] ADAPTATION_WORKAROUND_BUFFER = Util.getBytesFromHexString(
            "0000016742C00BDA259000000168CE0F13200000016588840DCE7118A0002FBF1C31C3275D78");

    private static final int ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT = 32;

    private final MediaCodecSelector mediaCodecSelector;
    private final DrmSessionManager<FrameworkMediaCrypto> drmSessionManager;
    private final boolean playClearSamplesWithoutKeys;
    private final DecoderInputBuffer buffer;
    private final FormatHolder formatHolder;
    private final List<Long> decodeOnlyPresentationTimestamps;
    private final MediaCodec.BufferInfo outputBufferInfo;

    private Format format;
    private MediaCodec codec;
    private DrmSession<FrameworkMediaCrypto> drmSession;
    private DrmSession<FrameworkMediaCrypto> pendingDrmSession;
    private boolean codecIsAdaptive;
    private boolean codecNeedsDiscardToSpsWorkaround;
    private boolean codecNeedsFlushWorkaround;
    private boolean codecNeedsAdaptationWorkaround;
    private boolean codecNeedsEosPropagationWorkaround;
    private boolean codecNeedsEosFlushWorkaround;
    private boolean codecNeedsMonoChannelCountWorkaround;
    private boolean codecNeedsAdaptationWorkaroundBuffer;
    private boolean shouldSkipAdaptationWorkaroundOutputBuffer;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private long codecHotswapDeadlineMs;
    private int inputIndex;
    private int outputIndex;
    private boolean shouldSkipOutputBuffer;
    private boolean codecReconfigured;
    private int codecReconfigurationState;
    private int codecReinitializationState;
    private boolean codecReceivedBuffers;
    private boolean codecReceivedEos;

    private boolean inputStreamEnded;
    private boolean outputStreamEnded;
    private boolean waitingForKeys;

    protected DecoderCounters decoderCounters;

    public MediaCodecRenderer(int trackType, MediaCodecSelector mediaCodecSelector,
                              DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                              boolean playClearSamplesWithoutKeys) {
        super(trackType);
        Assertions.checkState(Util.SDK_INT >= 16);
        this.mediaCodecSelector = Assertions.checkNotNull(mediaCodecSelector);
        this.drmSessionManager = drmSessionManager;
        this.playClearSamplesWithoutKeys = playClearSamplesWithoutKeys;
        buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
        formatHolder = new FormatHolder();
        decodeOnlyPresentationTimestamps = new ArrayList<>();
        outputBufferInfo = new MediaCodec.BufferInfo();
        codecReconfigurationState = RECONFIGURATION_STATE_NONE;
        codecReinitializationState = REINITIALIZATION_STATE_NONE;
    }

    @Override
    public int supportsMixedMimeTypeAdaptation() throws ExoPlaybackException {
        return ADAPTIVE_NOT_SEAMLESS;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
        try {
            return supportsFormat(mediaCodecSelector, format);
        } catch (DecoderQueryException e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
    }

    protected abstract int supportsFormat(MediaCodecSelector mediaCodecSelector, Format format)
            throws DecoderQueryException;

    protected MediaCodecInfo getDecoderInfo(MediaCodecSelector mediaCodecSelector,
                                            Format format, boolean requiresSecureDecoder) throws DecoderQueryException {
        return mediaCodecSelector.getDecoderInfo(format.sampleMimeType, requiresSecureDecoder);
    }

    protected abstract void configureCodec(MediaCodec codec, Format format, MediaCrypto crypto);

    @SuppressWarnings("deprecation")
    protected final void maybeInitCodec() throws ExoPlaybackException {
        if (!shouldInitCodec()) {
            return;
        }

        drmSession = pendingDrmSession;
        String mimeType = format.sampleMimeType;
        MediaCrypto mediaCrypto = null;
        boolean drmSessionRequiresSecureDecoder = false;
        if (drmSession != null) {
            @DrmSession.State int drmSessionState = drmSession.getState();
            if (drmSessionState == DrmSession.STATE_ERROR) {
                throw ExoPlaybackException.createForRenderer(drmSession.getError(), getIndex());
            } else if (drmSessionState == DrmSession.STATE_OPENED
                    || drmSessionState == DrmSession.STATE_OPENED_WITH_KEYS) {
                mediaCrypto = drmSession.getMediaCrypto().getWrappedMediaCrypto();
                drmSessionRequiresSecureDecoder = drmSession.requiresSecureDecoderComponent(mimeType);
            } else {
                return;
            }
        }

        MediaCodecInfo decoderInfo = null;
        try {
            decoderInfo = getDecoderInfo(mediaCodecSelector, format, drmSessionRequiresSecureDecoder);
            if (decoderInfo == null && drmSessionRequiresSecureDecoder) {
                decoderInfo = getDecoderInfo(mediaCodecSelector, format, false);
                if (decoderInfo != null) {
                    Log.w(TAG, "Drm session requires secure decoder for " + mimeType + ", but "
                            + "no secure decoder available. Trying to proceed with " + decoderInfo.name + ".");
                }
            }
        } catch (DecoderQueryException e) {
            throwDecoderInitError(new DecoderInitializationException(format, e,
                    drmSessionRequiresSecureDecoder, DecoderInitializationException.DECODER_QUERY_ERROR));
        }
        if (decoderInfo == null) {
            throwDecoderInitError(new DecoderInitializationException(format, null,
                    drmSessionRequiresSecureDecoder, DecoderInitializationException.NO_SUITABLE_DECODER_ERROR));
        }

        String codecName = decoderInfo.name;
        codecIsAdaptive = decoderInfo.adaptive;
        codecNeedsDiscardToSpsWorkaround = codecNeedsDiscardToSpsWorkaround(codecName, format);
        codecNeedsFlushWorkaround = codecNeedsFlushWorkaround(codecName);
        codecNeedsAdaptationWorkaround = codecNeedsAdaptationWorkaround(codecName);
        codecNeedsEosPropagationWorkaround = codecNeedsEosPropagationWorkaround(codecName);
        codecNeedsEosFlushWorkaround = codecNeedsEosFlushWorkaround(codecName);
        codecNeedsMonoChannelCountWorkaround = codecNeedsMonoChannelCountWorkaround(codecName, format);

        try {
            long codecInitializingTimestamp = SystemClock.elapsedRealtime();
            TraceUtil.beginSection("createCodec:" + codecName);
            codec = MediaCodec.createByCodecName(codecName);
            TraceUtil.endSection();
            TraceUtil.beginSection("configureCodec");
            configureCodec(codec, format, mediaCrypto);
            TraceUtil.endSection();
            TraceUtil.beginSection("startCodec");
            codec.start();
            TraceUtil.endSection();
            long codecInitializedTimestamp = SystemClock.elapsedRealtime();
            onCodecInitialized(codecName, codecInitializedTimestamp,
                    codecInitializedTimestamp - codecInitializingTimestamp);
            inputBuffers = codec.getInputBuffers();
            outputBuffers = codec.getOutputBuffers();
        } catch (Exception e) {
            throwDecoderInitError(new DecoderInitializationException(format, e,
                    drmSessionRequiresSecureDecoder, codecName));
        }
        codecHotswapDeadlineMs = getState() == STATE_STARTED
                ? (SystemClock.elapsedRealtime() + MAX_CODEC_HOTSWAP_TIME_MS) : C.TIME_UNSET;
        inputIndex = C.INDEX_UNSET;
        outputIndex = C.INDEX_UNSET;
        decoderCounters.decoderInitCount++;
    }

    private void throwDecoderInitError(DecoderInitializationException e)
            throws ExoPlaybackException {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
    }

    protected boolean shouldInitCodec() {
        return codec == null && format != null;
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        decoderCounters = new DecoderCounters();
    }

    @Override
    protected void onPositionReset(long positionUs, boolean joining) throws ExoPlaybackException {
        inputStreamEnded = false;
        outputStreamEnded = false;
        if (codec != null) {
            flushCodec();
        }
    }

    @Override
    protected void onDisabled() {
        format = null;
        try {
            releaseCodec();
        } finally {
            try {
                if (drmSession != null) {
                    drmSessionManager.releaseSession(drmSession);
                }
            } finally {
                try {
                    if (pendingDrmSession != null && pendingDrmSession != drmSession) {
                        drmSessionManager.releaseSession(pendingDrmSession);
                    }
                } finally {
                    drmSession = null;
                    pendingDrmSession = null;
                }
            }
        }
    }

    protected void releaseCodec() {
        if (codec != null) {
            codecHotswapDeadlineMs = C.TIME_UNSET;
            inputIndex = C.INDEX_UNSET;
            outputIndex = C.INDEX_UNSET;
            waitingForKeys = false;
            shouldSkipOutputBuffer = false;
            decodeOnlyPresentationTimestamps.clear();
            inputBuffers = null;
            outputBuffers = null;
            codecReconfigured = false;
            codecReceivedBuffers = false;
            codecIsAdaptive = false;
            codecNeedsDiscardToSpsWorkaround = false;
            codecNeedsFlushWorkaround = false;
            codecNeedsAdaptationWorkaround = false;
            codecNeedsEosPropagationWorkaround = false;
            codecNeedsEosFlushWorkaround = false;
            codecNeedsMonoChannelCountWorkaround = false;
            codecNeedsAdaptationWorkaroundBuffer = false;
            shouldSkipAdaptationWorkaroundOutputBuffer = false;
            codecReceivedEos = false;
            codecReconfigurationState = RECONFIGURATION_STATE_NONE;
            codecReinitializationState = REINITIALIZATION_STATE_NONE;
            decoderCounters.decoderReleaseCount++;

            try {
                codec.stop();
            } finally {
                try {
                    codec.release();
                } finally {
                    codec = null;
                    if (drmSession != null && pendingDrmSession != drmSession) {
                        try {
                            drmSessionManager.releaseSession(drmSession);
                        } finally {
                            drmSession = null;
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void onStarted() {

    }

    @Override
    protected void onStopped() {

    }

    @Override
    public boolean isEnded() {
        return outputStreamEnded;
    }

    @Override
    public boolean isReady() {
        return format != null && !waitingForKeys && (isSourceReady() || outputIndex >= 0
                || (codecHotswapDeadlineMs != C.TIME_UNSET
                && SystemClock.elapsedRealtime() < codecHotswapDeadlineMs));
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        if (format == null) {
            readFormat();
        }
        maybeInitCodec();
        if (codec != null) {
            TraceUtil.beginSection("drainAndFeed");
            //noinspection StatementWithEmptyBody
            while (drainOutputBuffer(positionUs, elapsedRealtimeUs)) {
            }
            //noinspection StatementWithEmptyBody
            while (feedInputBuffer()) {
            }
            TraceUtil.endSection();
        } else if (format != null) {
            skipToKeyframeBefore(positionUs);
        }
        decoderCounters.ensureUpdated();
    }

    private void readFormat() throws ExoPlaybackException {
        int result = readSource(formatHolder, null);
        if (result == C.RESULT_FORMAT_READ) {
            onInputFormatChanged(formatHolder.format);
        }
    }

    protected void onCodecInitialized(String name, long initializedTimestampMs,
                                      long initializationDurationMs) {

    }

    protected void onInputFormatChanged(Format newFormat) throws ExoPlaybackException {
        Format oldFormat = format;
        format = newFormat;

        boolean drmInitDataChanged = !Util.areEqual(format.drmInitData, oldFormat == null ? null
                : oldFormat.drmInitData);
        if (drmInitDataChanged) {
            if (format.drmInitData != null) {
                if (drmSessionManager == null) {
                    throw ExoPlaybackException.createForRenderer(
                            new IllegalStateException("Media requires a DrmSessionManager"), getIndex());
                }
                pendingDrmSession = drmSessionManager.acquireSession(Looper.myLooper(), format.drmInitData);
                if (pendingDrmSession == drmSession) {
                    drmSessionManager.releaseSession(pendingDrmSession);
                }
            } else {
                pendingDrmSession = null;
            }
        }

        if (pendingDrmSession == drmSession && codec != null
                && canReconfigureCodec(codec, codecIsAdaptive, oldFormat, format)) {
            codecReconfigured = true;
            codecReconfigurationState = RECONFIGURATION_STATE_WRITE_RENDING;
            codecNeedsAdaptationWorkaroundBuffer = oldFormat != null && (codecNeedsAdaptationWorkaround
                    && format.width == oldFormat.width && format.height == oldFormat.height);
        } else {
            if (codecReceivedBuffers) {
                codecReconfigurationState = REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM;
            } else {
                releaseCodec();
                maybeInitCodec();
            }
        }
    }

    protected boolean canReconfigureCodec(MediaCodec codec, boolean codecIsAdaptive, Format oldFormat,
                                          Format newFormat) {
        return false;
    }

    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {

    }

    protected void onProcessedOutputBuffer(long presentationTimeUs) {

    }

    protected void onOutputFormatChanged(MediaCodec codec, MediaFormat outputFormat) {

    }

    protected void onOutputStreamEnded() {

    }

    protected void flushCodec() throws ExoPlaybackException {
        codecHotswapDeadlineMs = C.TIME_UNSET;
        inputIndex = C.INDEX_UNSET;
        outputIndex = C.INDEX_UNSET;
        waitingForKeys = false;
        shouldSkipOutputBuffer = false;
        decodeOnlyPresentationTimestamps.clear();
        codecNeedsAdaptationWorkaroundBuffer = false;
        shouldSkipAdaptationWorkaroundOutputBuffer = false;
        if (codecNeedsFlushWorkaround || (codecNeedsEosFlushWorkaround && codecReceivedEos)) {
            releaseCodec();
            maybeInitCodec();
        } else if (codecReinitializationState != REINITIALIZATION_STATE_NONE) {
            releaseCodec();
            maybeInitCodec();
        } else {
            codec.flush();
            codecReceivedBuffers = false;
        }
        if (codecReconfigured && format != null) {
            codecReconfigurationState = RECONFIGURATION_STATE_WRITE_RENDING;
        }
    }

    private boolean feedInputBuffer() throws ExoPlaybackException {
        if (inputStreamEnded
                || codecReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
            return false;
        }

        if (inputIndex < 0) {
            inputIndex = codec.dequeueInputBuffer(0);
            if (inputIndex < 0) {
                return false;
            }
            buffer.data = inputBuffers[inputIndex];
            buffer.clear();
        }

        if (codecReinitializationState == REINITIALIZATION_STATE_SIGNAL_END_OF_STREAM) {
            //noinspection StatementWithEmptyBody
            if (codecNeedsEosPropagationWorkaround) {
            } else {
                codecReceivedEos = true;
                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                inputIndex = C.INDEX_UNSET;
            }
            codecReinitializationState = REINITIALIZATION_STATE_WAIT_END_OF_STREAM;
            return false;
        }

        if (codecNeedsAdaptationWorkaroundBuffer) {
            codecNeedsAdaptationWorkaroundBuffer = false;
            buffer.data.put(ADAPTATION_WORKAROUND_BUFFER);
            codec.queueInputBuffer(inputIndex, 0, ADAPTATION_WORKAROUND_BUFFER.length, 0, 0);
            inputIndex = C.INDEX_UNSET;
            codecReceivedBuffers = true;
            return true;
        }

        int result;
        int adaptiveReconfigurationBytes = 0;
        if (waitingForKeys) {
            result = C.RESULT_BUFFER_READ;
        } else {
            if (codecReconfigurationState == RECONFIGURATION_STATE_WRITE_RENDING) {
                for (int i = 0; i < format.initializationData.size(); i++) {
                    byte[] data = format.initializationData.get(i);
                    buffer.data.put(data);
                }
                codecReconfigurationState = RECONFIGURATION_STATE_QUEUE_PENDING;
            }
            adaptiveReconfigurationBytes = buffer.data.position();
            result = readSource(formatHolder, buffer);
        }

        if (result == C.RESULT_NOTHING_READ) {
            return false;
        }

        if (result == C.RESULT_FORMAT_READ) {
            if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
                buffer.clear();
                codecReinitializationState = RECONFIGURATION_STATE_WRITE_RENDING;
            }
            onInputFormatChanged(formatHolder.format);
            return true;
        }

        if (buffer.isEndOfStream()) {
            if (codecReconfigurationState == RECONFIGURATION_STATE_QUEUE_PENDING) {
                buffer.clear();
                codecReconfigurationState = RECONFIGURATION_STATE_WRITE_RENDING;
            }
            inputStreamEnded = true;
            if (!codecReceivedBuffers) {
                processEndOfStream();
                return false;
            }
            try {
                //noinspection StatementWithEmptyBody
                if (codecNeedsEosPropagationWorkaround) {

                } else {
                    codecReceivedEos = true;
                    codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputIndex = C.INDEX_UNSET;
                }
            } catch (CryptoException e) {
                throw ExoPlaybackException.createForRenderer(e, getIndex());
            }
            return false;
        }
        boolean bufferEncrypted = buffer.isEncrypted();
        waitingForKeys = shouldWaitForKeys(bufferEncrypted);
        if (waitingForKeys) {
            return false;
        }
        if (codecNeedsDiscardToSpsWorkaround && !bufferEncrypted) {
            NalUnitUtil.discardToSps(buffer.data);
            if (buffer.data.position() == 0) {
                return true;
            }
            codecNeedsDiscardToSpsWorkaround = false;
        }
        try {
            long presentationTimeUs = buffer.timeUs;
            if (buffer.isDecodeOnly()) {
                decodeOnlyPresentationTimestamps.add(presentationTimeUs);
            }
            buffer.flip();
            onQueueInputBuffer(buffer);

            if (bufferEncrypted) {
                MediaCodec.CryptoInfo cryptoInfo = getFrameworkCryptoInfo(buffer,
                        adaptiveReconfigurationBytes);
                codec.queueSecureInputBuffer(inputIndex, 0, cryptoInfo, presentationTimeUs, 0);
            } else {
                codec.queueInputBuffer(inputIndex, 0, buffer.data.limit(), presentationTimeUs, 0);
            }
            inputIndex = C.INDEX_UNSET;
            codecReceivedBuffers = true;
            codecReconfigurationState = RECONFIGURATION_STATE_NONE;
            decoderCounters.inputBufferCount++;
        } catch (CryptoException e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
        return true;
    }

    protected long getDequeueOutputBufferTimeoutUs() {
        return 0;
    }

    @SuppressWarnings("deprecation")
    private boolean drainOutputBuffer(long positionUs, long elapsedRealtimeUs)
            throws ExoPlaybackException {
        if (outputStreamEnded) {
            return false;
        }

        if (outputIndex < 0) {
            outputIndex = codec.dequeueOutputBuffer(outputBufferInfo, getDequeueOutputBufferTimeoutUs());
            if (outputIndex >= 0) {
                if (shouldSkipAdaptationWorkaroundOutputBuffer) {
                    shouldSkipAdaptationWorkaroundOutputBuffer = false;
                    codec.releaseOutputBuffer(outputIndex, false);
                    outputIndex = C.INDEX_UNSET;
                    return true;
                }
                if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    processEndOfStream();
                    outputIndex = C.INDEX_UNSET;
                    return true;
                } else {
                    ByteBuffer outputBuffer = outputBuffers[outputIndex];
                    if (outputBuffer != null) {
                        outputBuffer.position(outputBufferInfo.offset);
                        outputBuffer.limit(outputBufferInfo.offset + outputBufferInfo.size);
                    }
                    shouldSkipOutputBuffer = shouldSkipOutputBuffer(outputBufferInfo.presentationTimeUs);
                }
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                processOutputFormat();
                return true;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                processOutputBuffersChanged();
                return true;
            } else {
                if (codecNeedsEosPropagationWorkaround && (inputStreamEnded
                        || codecReconfigurationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM)) {
                    processEndOfStream();
                    return true;
                }
                return false;
            }
        }

        if (processOutputBuffer(positionUs, elapsedRealtimeUs, codec, outputBuffers[outputIndex],
                outputIndex, outputBufferInfo.flags, outputBufferInfo.presentationTimeUs,
                shouldSkipOutputBuffer)) {
            onProcessedOutputBuffer(outputBufferInfo.presentationTimeUs);
            outputIndex = C.INDEX_UNSET;
            return true;
        }
        return false;
    }

    protected abstract boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs,
                                                   MediaCodec codec, ByteBuffer buffer, int bufferIndex, int bufferFlags,
                                                   long bufferPresentationTimeUs, boolean shouldSkip) throws ExoPlaybackException;

    private void processOutputFormat() {
        MediaFormat format = codec.getOutputFormat();
        if (codecNeedsAdaptationWorkaround
                && format.getInteger(MediaFormat.KEY_WIDTH) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT
                && format.getInteger(MediaFormat.KEY_HEIGHT) == ADAPTATION_WORKAROUND_SLICE_WIDTH_HEIGHT) {
            shouldSkipAdaptationWorkaroundOutputBuffer = true;
            return;
        }
        if (codecNeedsMonoChannelCountWorkaround) {
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        }
        onOutputFormatChanged(codec, format);
    }

    @SuppressWarnings("deprecation")
    private void processOutputBuffersChanged() {
        outputBuffers = codec.getOutputBuffers();
    }

    private void processEndOfStream() throws ExoPlaybackException {
        if (codecReinitializationState == REINITIALIZATION_STATE_WAIT_END_OF_STREAM) {
            releaseCodec();
            maybeInitCodec();
        } else {
            outputStreamEnded = true;
            onOutputStreamEnded();
        }
    }

    private boolean shouldWaitForKeys(boolean bufferEncrypted) throws ExoPlaybackException {
        if (drmSession == null) {
            return false;
        }
        @DrmSession.State int drmSessionState = drmSession.getState();
        if (drmSessionState == DrmSession.STATE_ERROR) {
            throw ExoPlaybackException.createForRenderer(drmSession.getError(), getIndex());
        }
        return drmSessionState != DrmSession.STATE_OPENED_WITH_KEYS
                && (bufferEncrypted || !playClearSamplesWithoutKeys);
    }

    private boolean shouldSkipOutputBuffer(long presentationTimeUs) {
        int size = decodeOnlyPresentationTimestamps.size();
        for (int i = 0; i < size; i++) {
            if (decodeOnlyPresentationTimestamps.get(i) == presentationTimeUs) {
                decodeOnlyPresentationTimestamps.remove(i);
                return true;
            }
        }
        return false;
    }

    private static MediaCodec.CryptoInfo getFrameworkCryptoInfo(DecoderInputBuffer buffer,
                                                                int adaptiveReconfigurationBytes) {
        MediaCodec.CryptoInfo cryptoInfo = buffer.cryptoInfo.getFrameworkCryptoInfoV16();
        if (adaptiveReconfigurationBytes == 0) {
            return cryptoInfo;
        }
        if (cryptoInfo.numBytesOfClearData == null) {
            cryptoInfo.numBytesOfClearData = new int[1];
        }
        cryptoInfo.numBytesOfClearData[0] += adaptiveReconfigurationBytes;
        return cryptoInfo;
    }

    private static boolean codecNeedsDiscardToSpsWorkaround(String name, Format format) {
        return Util.SDK_INT < 21 && format.initializationData.isEmpty()
                && "OMX.MTK.VIDEO.DECODER.AVC".equals(name);
    }

    private static boolean codecNeedsFlushWorkaround(String name) {
        return Util.SDK_INT < 18
                || (Util.SDK_INT == 18
                && ("OMX.SEC.avc.dec".equals(name) || "OMX.SEC.avc.dec.secure".equals(name)))
                || (Util.SDK_INT == 19 && Util.MODEL.startsWith("SM-G800")
                && ("OMX.Exynos.avc.dec".equals(name) || "OMX.Exynos.avc.dec.secure".equals(name)));
    }

    private static boolean codecNeedsAdaptationWorkaround(String name) {
        return Util.SDK_INT < 24
                && ("OMX.Nvidia.h264.decode".equals(name) || "OMX.Nvidia.h264.decode.secure".equals(name))
                && ("flounder".equals(Util.DEVICE) || "flounder_lte".equals(Util.DEVICE)
                || "grouper".equals(Util.DEVICE) || "tilapia".equals(Util.DEVICE));
    }

    private static boolean codecNeedsEosPropagationWorkaround(String name) {
        return Util.SDK_INT <= 17 && ("OMX.rk.video_decoder.avc".equals(name)
                || "OMX.allwinner.video.decoder.avc".equals(name));
    }

    private static boolean codecNeedsEosFlushWorkaround(String name) {
        return Util.SDK_INT <= 23 && "OMX.google.vorbis.decoder".equals(name);
    }

    private static boolean codecNeedsMonoChannelCountWorkaround(String name, Format format) {
        return Util.SDK_INT <= 18 && format.channelCount == 1
                && "OMX.MTK.AUDIO.DECODER.MP3".equals(name);
    }

}
