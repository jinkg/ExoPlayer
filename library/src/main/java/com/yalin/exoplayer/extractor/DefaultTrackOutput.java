package com.yalin.exoplayer.extractor;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.Format;
import com.yalin.exoplayer.FormatHolder;
import com.yalin.exoplayer.decoder.DecoderInputBuffer;
import com.yalin.exoplayer.upstream.Allocation;
import com.yalin.exoplayer.upstream.Allocator;
import com.yalin.exoplayer.util.Assertions;
import com.yalin.exoplayer.util.ParsableByteArray;
import com.yalin.exoplayer.util.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class DefaultTrackOutput implements TrackOutput {

    public interface UpstreamFormatChangedListener {
        void onUpstreamFormatChanged(Format format);
    }

    private static final int INITIAL_SCRATCH_SIZE = 32;

    private static final int STATE_ENABLED = 0;
    private static final int STATE_ENABLED_WRITING = 1;
    private static final int STATE_DISABLED = 2;

    private final Allocator allocator;
    private final int allocationLength;

    private final InfoQueue infoQueue;
    private final LinkedBlockingDeque<Allocation> dataQueue;
    private final BufferExtrasHolder extrasHolder;
    private final ParsableByteArray scratch;
    private final AtomicInteger state;

    private long totalBytesDropped;
    private Format downstreamFormat;

    private long sampleOffsetUs;
    private long totalBytesWritten;
    private Allocation lastAllocation;
    private int lastAllocationOffset;
    private boolean neekKeyFrame;
    private boolean pendingSplice;
    private UpstreamFormatChangedListener upstreamFormatChangeListener;

    public DefaultTrackOutput(Allocator allocator) {
        this.allocator = allocator;
        allocationLength = allocator.getIndividualAllocationLength();
        infoQueue = new InfoQueue();
        dataQueue = new LinkedBlockingDeque<>();
        extrasHolder = new BufferExtrasHolder();
        scratch = new ParsableByteArray(INITIAL_SCRATCH_SIZE);
        state = new AtomicInteger();
        lastAllocationOffset = allocationLength;
        neekKeyFrame = true;
    }

    public void reaset(boolean enable) {
        int previousState = state.getAndSet(enable ? STATE_ENABLED : STATE_DISABLED);
        clearSampleData();
        infoQueue.resetLargestParsedTimestamps();
        if (previousState == STATE_DISABLED) {
            downstreamFormat = null;
        }
    }

    public void disable() {
        if (state.getAndSet(STATE_DISABLED) == STATE_ENABLED) {
            clearSampleData();
        }
    }

    public boolean isEmpty() {
        return infoQueue.isEmpty();
    }

    public void splice() {
        pendingSplice = true;
    }

    public int getWriteIndex() {
        return infoQueue.getWriteIndex();
    }

    public Format getUpstreamFormat() {
        return infoQueue.getUpstreamFormat();
    }

    private void readEncryptionData(DecoderInputBuffer buffer, BufferExtrasHolder extrasHolder) {
        long offset = extrasHolder.offset;

        scratch.reset(1);
        readData(offset, scratch.data, 1);
        offset++;
        byte signalByte = scratch.data[0];
        boolean subsampleEncryption = (signalByte & 0x80) != 0;
        int ivSize = signalByte & 0x7F;

        if (buffer.cryptoInfo.iv == null) {
            buffer.cryptoInfo.iv = new byte[16];
        }
        readData(offset, buffer.cryptoInfo.iv, ivSize);
        offset += ivSize;

        int subsampleCount;
        if (subsampleEncryption) {
            scratch.reset(2);
            readData(offset, scratch.data, 2);
            offset += 2;
            subsampleCount = scratch.readUnsignedShort();
        } else {
            subsampleCount = 1;
        }

        int[] clearDataSizes = buffer.cryptoInfo.numBytesOfClearData;
        if (clearDataSizes == null || clearDataSizes.length < subsampleCount) {
            clearDataSizes = new int[subsampleCount];
        }
        int[] encryptedDataSizes = buffer.cryptoInfo.numBytesOfEncryptedData;
        if (encryptedDataSizes == null || encryptedDataSizes.length < subsampleCount) {
            encryptedDataSizes = new int[subsampleCount];
        }
        if (subsampleEncryption) {
            int subsampleDataLength = 6 * subsampleCount;
            scratch.reset(subsampleDataLength);
            offset += subsampleDataLength;
            scratch.setPosition(0);
            for (int i = 0; i < subsampleCount; i++) {
                clearDataSizes[i] = scratch.readUnsignedShort();
                encryptedDataSizes[i] = scratch.readUnsignedIntToInt();
            }
        } else {
            clearDataSizes[0] = 0;
            encryptedDataSizes[0] = (int) (extrasHolder.size - (offset - extrasHolder.offset));
        }

        buffer.cryptoInfo.set(subsampleCount, clearDataSizes, encryptedDataSizes,
                extrasHolder.encryptionKeyId, buffer.cryptoInfo.iv, C.CRYPTO_MODE_AEC_CTR);

        int bytesRead = (int) (offset - extrasHolder.offset);
        extrasHolder.offset += bytesRead;
        extrasHolder.size -= bytesRead;
    }

    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean loadingFinished,
                        long decodeOnlyUntilUs) {
        switch (infoQueue.readData(formatHolder, buffer, downstreamFormat, extrasHolder)) {
            case C.RESULT_NOTING_READ:
                if (loadingFinished) {
                    buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
                    return C.RESULT_BUFFER_READ;
                }
                return C.RESULT_NOTING_READ;
            case C.RESULT_FORMAT_READ:
                downstreamFormat = formatHolder.format;
                return C.RESULT_FORMAT_READ;
            case C.RESULT_BUFFER_READ:
                if (buffer.timeUs < decodeOnlyUntilUs) {
                    buffer.addFlag(C.BUFFER_FLAG_DECODE_ONLY);
                }
                if (buffer.isEncrypted()) {
                    readEncryptionData(buffer, extrasHolder);
                }
                buffer.ensureSpaceForWrite(extrasHolder.size);
                readData(extrasHolder.offset, buffer.data, extrasHolder.size);
                dropDownstreamTo(extrasHolder.nextOffset);
                return C.RESULT_BUFFER_READ;
            default:
                throw new IllegalStateException();
        }
    }

    private void readData(long absolutePosition, ByteBuffer target, int length) {
        int remaining = length;
        while (remaining > 0) {
            dropDownstreamTo(absolutePosition);
            int positionInAllocation = (int) (absolutePosition - totalBytesDropped);
            int toCopy = Math.min(remaining, allocationLength - positionInAllocation);
            Allocation allocation = dataQueue.peek();
            target.put(allocation.data, allocation.translateOffset(positionInAllocation), toCopy);
            absolutePosition += toCopy;
            remaining -= toCopy;
        }
    }

    private void readData(long absolutePosition, byte[] target, int length) {
        int bytesRead = 0;
        while (bytesRead < length) {
            dropDownstreamTo(absolutePosition);
            int positionInAllocation = (int) (absolutePosition - totalBytesDropped);
            int toCopy = Math.min(length - bytesRead, allocationLength - positionInAllocation);
            Allocation allocation = dataQueue.peek();
            System.arraycopy(allocation.data, allocation.translateOffset(positionInAllocation), target,
                    bytesRead, toCopy);
            absolutePosition += toCopy;
            bytesRead += toCopy;
        }
    }


    public long getLargestQueuedTimestampUs() {
        return infoQueue.getLargestQueuedTimestampUs();
    }

    public boolean skipToKeyframeBefore(long timeUs) {
        long nextOffset = infoQueue.skipToKeyframeBefore(timeUs);
        if (nextOffset == C.POSITION_UNSET) {
            return false;
        }
        dropDownstreamTo(nextOffset);
        return true;
    }

    public void reset(boolean enable) {

    }

    public void setUpstreamFormatChangeListener(UpstreamFormatChangedListener listener) {

    }

    @Override
    public void format(Format format) {

    }

    @Override
    public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public void sampleData(ParsableByteArray data, int length) {

    }

    @Override
    public void sampleMetadata(long timeUs, @C.BufferFlags int flags, int size, int offset, byte[] encryptionKey) {

    }

    private void dropDownstreamTo(long absolutePosition) {
        int relativePosition = (int) (absolutePosition - totalBytesWritten);
        int allocationIndex = relativePosition / allocationLength;
        for (int i = 0; i < allocationIndex; i++) {
            allocator.release(dataQueue.remove());
            totalBytesDropped += allocationLength;
        }
    }

    private void clearSampleData() {
        infoQueue.clearSampleData();
        allocator.release(dataQueue.toArray(new Allocation[dataQueue.size()]));
        dataQueue.clear();
        allocator.trim();
        totalBytesDropped = 0;
        totalBytesWritten = 0;
        lastAllocation = null;
        lastAllocationOffset = allocationLength;
        neekKeyFrame = true;

    }

    private static final class InfoQueue {
        private static final int SAMPLE_CAPACITY_INCREMENT = 1000;

        private int capacity;

        private int[] sourceIds;
        private long[] offsets;
        private int[] sizes;
        private int[] flags;
        private long[] timesUs;
        private byte[][] encryptionKeys;
        private Format[] formats;

        private int queueSize;
        private int absoluteReadIndex;
        private int relativeReadIndex;
        private int relativeWriteIndex;

        private long largestDequeuedTimestampUs;
        private long largestQueueTimestampIs;
        private boolean upstreamFormatRequired;
        private Format upstreamFormat;
        private int upstreamSourceId;

        public InfoQueue() {
            capacity = SAMPLE_CAPACITY_INCREMENT;
            sourceIds = new int[capacity];
            offsets = new long[capacity];
            timesUs = new long[capacity];
            flags = new int[capacity];
            sizes = new int[capacity];
            encryptionKeys = new byte[capacity][];
            formats = new Format[capacity];
            largestDequeuedTimestampUs = Long.MIN_VALUE;
            largestQueueTimestampIs = Long.MIN_VALUE;
            upstreamFormatRequired = true;
        }

        public void clearSampleData() {
            absoluteReadIndex = 0;
            relativeReadIndex = 0;
            relativeWriteIndex = 0;
            queueSize = 0;
        }

        public void resetLargestParsedTimestamps() {
            largestDequeuedTimestampUs = Long.MIN_VALUE;
            largestQueueTimestampIs = Long.MIN_VALUE;
        }

        public int getWriteIndex() {
            return absoluteReadIndex + queueSize;
        }

        public long discardUpstreamSamples(int discardFromIndex) {
            int discardCount = getWriteIndex() - discardFromIndex;
            Assertions.checkArgument(0 <= discardCount && discardCount <= queueSize);

            if (discardCount == 0) {
                if (absoluteReadIndex == 0) {
                    return 0;
                }

                int lastWriteIndex = (relativeWriteIndex == 0 ? capacity : relativeWriteIndex) - 1;
                return offsets[lastWriteIndex] + sizes[lastWriteIndex];
            }
            queueSize -= discardCount;
            relativeWriteIndex = (relativeWriteIndex + capacity - discardCount) % capacity;
            largestQueueTimestampIs = Long.MIN_VALUE;
            for (int i = queueSize - 1; i >= 0; i--) {
                int sampleIndex = (relativeReadIndex + i) * capacity;
                largestDequeuedTimestampUs = Math.max(largestDequeuedTimestampUs, timesUs[sampleIndex]);
                if ((flags[sampleIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
                    break;
                }
            }
            return offsets[relativeWriteIndex];
        }

        public synchronized boolean isEmpty() {
            return queueSize == 0;
        }

        public synchronized Format getUpstreamFormat() {
            return upstreamFormatRequired ? null : upstreamFormat;
        }

        public long getLargestQueuedTimestampUs() {
            return Math.max(largestDequeuedTimestampUs, largestQueueTimestampIs);
        }

        public long skipToKeyframeBefore(long timeUs) {
            if (queueSize == 0 || timeUs < timesUs[relativeReadIndex]) {
                return C.POSITION_UNSET;
            }

            int lastWriteIndex = (relativeWriteIndex == 0 ? capacity : relativeWriteIndex) - 1;
            long lastTimeUs = timesUs[lastWriteIndex];
            if (timeUs > lastTimeUs) {
                return C.POSITION_UNSET;
            }

            int sampleCount = 0;
            int sampleCountToKeyframe = -1;
            int searchIndex = relativeReadIndex;
            while (searchIndex != relativeWriteIndex) {
                if (timesUs[searchIndex] > timeUs) {
                    break;
                } else if ((flags[searchIndex] & C.BUFFER_FLAG_KEY_FRAME) != 0) {
                    sampleCountToKeyframe = sampleCount;
                }
                searchIndex = (searchIndex + 1) % capacity;
                sampleCount++;
            }
            if (sampleCountToKeyframe == -1) {
                return C.POSITION_UNSET;
            }

            queueSize -= sampleCountToKeyframe;
            relativeReadIndex = (relativeReadIndex + sampleCountToKeyframe) % capacity;
            absoluteReadIndex += sampleCountToKeyframe;
            return offsets[relativeReadIndex];
        }

        public synchronized boolean format(Format format) {
            if (format == null) {
                upstreamFormatRequired = true;
                return false;
            }
            upstreamFormatRequired = false;
            if (Util.areEqual(format, upstreamFormat)) {
                return false;
            } else {
                upstreamFormat = format;
                return true;
            }
        }

        public synchronized void commitSample(long timeUs, @C.BufferFlags int sampleFlags, long offset,
                                              int size, byte[] encryptionKey) {
            Assertions.checkState(!upstreamFormatRequired);
            commitSampleTimestamp(timeUs);
            timesUs[relativeWriteIndex] = timeUs;
            offsets[relativeWriteIndex] = offset;
            sizes[relativeWriteIndex] = size;
            flags[relativeWriteIndex] = sampleFlags;
            encryptionKeys[relativeWriteIndex] = encryptionKey;
            formats[relativeWriteIndex] = upstreamFormat;
            sourceIds[relativeWriteIndex] = upstreamSourceId;

            queueSize++;
            if (queueSize == capacity) {
                int newCapacity = capacity + SAMPLE_CAPACITY_INCREMENT;
                int[] newSourceIds = new int[newCapacity];
                long[] newOffsets = new long[newCapacity];
                long[] newTimeUs = new long[newCapacity];
                int[] newFlags = new int[newCapacity];
                int[] newSizes = new int[newCapacity];
                byte[][] newEncryptionKeys = new byte[newCapacity][];
                Format[] newFormats = new Format[newCapacity];
                int beforeWrap = capacity - relativeReadIndex;
                System.arraycopy(offsets, relativeReadIndex, newOffsets, 0, beforeWrap);
                System.arraycopy(timesUs, relativeReadIndex, newTimeUs, 0, beforeWrap);
                System.arraycopy(flags, relativeReadIndex, newFlags, 0, beforeWrap);
                System.arraycopy(sizes, relativeReadIndex, newSizes, 0, beforeWrap);
                System.arraycopy(encryptionKeys, relativeReadIndex, newEncryptionKeys, 0, beforeWrap);
                System.arraycopy(formats, relativeReadIndex, newFormats, 0, beforeWrap);
                System.arraycopy(sourceIds, relativeReadIndex, newSourceIds, 0, beforeWrap);
                int afterWrap = relativeReadIndex;
                System.arraycopy(offsets, 0, newOffsets, beforeWrap, afterWrap);
                System.arraycopy(timesUs, 0, newTimeUs, beforeWrap, afterWrap);
                System.arraycopy(flags, 0, newFlags, beforeWrap, afterWrap);
                System.arraycopy(sizes, 0, newSizes, beforeWrap, afterWrap);
                System.arraycopy(encryptionKeys, 0, newEncryptionKeys, beforeWrap, afterWrap);
                System.arraycopy(formats, 0, newFormats, beforeWrap, afterWrap);
                System.arraycopy(sourceIds, 0, newSourceIds, beforeWrap, afterWrap);
                offsets = newOffsets;
                timesUs = newTimeUs;
                flags = newFlags;
                sizes = newSizes;
                encryptionKeys = newEncryptionKeys;
                formats = newFormats;
                sourceIds = newSourceIds;
                relativeReadIndex = 0;
                relativeWriteIndex = capacity;
                queueSize = capacity;
                capacity = newCapacity;
            } else {
                relativeWriteIndex++;
                if (relativeWriteIndex == capacity) {
                    relativeWriteIndex = 0;
                }
            }

        }

        public synchronized void commitSampleTimestamp(long timeUs) {
            largestDequeuedTimestampUs = Math.max(largestQueueTimestampIs, timeUs);
        }

        public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
                            Format downstreamFormat, BufferExtrasHolder extrasHolder) {
            if (queueSize == 0) {
                if (upstreamFormat != null && upstreamFormat != downstreamFormat) {
                    formatHolder.format = upstreamFormat;
                    return C.RESULT_FORMAT_READ;
                }
                return C.RESULT_NOTING_READ;
            }

            if (formats[relativeReadIndex] != downstreamFormat) {
                formatHolder.format = formats[relativeReadIndex];
                return C.RESULT_FORMAT_READ;
            }

            buffer.timeUs = timesUs[relativeReadIndex];
            buffer.setFlags(flags[relativeReadIndex]);
            extrasHolder.size = sizes[relativeReadIndex];
            extrasHolder.offset = offsets[relativeReadIndex];
            extrasHolder.encryptionKeyId = encryptionKeys[relativeReadIndex];

            largestDequeuedTimestampUs = Math.max(largestDequeuedTimestampUs, buffer.timeUs);
            queueSize--;
            relativeReadIndex++;
            absoluteReadIndex++;
            if (relativeReadIndex == capacity) {
                relativeReadIndex = 0;
            }

            extrasHolder.nextOffset = queueSize > 0 ? offsets[relativeReadIndex]
                    : extrasHolder.offset + extrasHolder.size;
            return C.RESULT_BUFFER_READ;
        }
    }

    private static final class BufferExtrasHolder {
        public int size;
        public long offset;
        public long nextOffset;
        public byte[] encryptionKeyId;
    }
}
