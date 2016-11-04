package com.yalin.exoplayer.decoder;

import android.support.annotation.IntDef;

import com.yalin.exoplayer.C;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public class DecoderInputBuffer extends Buffer {

    /**
     * The buffer replacement mode, which may disable replacement.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BUFFER_REPLACEMENT_MODE_DISABLED, BUFFER_REPLACEMENT_MODE_NORMAL,
            BUFFER_REPLACEMENT_MODE_DIRECT})
    public @interface BufferReplacementMode {
    }

    /**
     * Disallows buffer replacement.
     */
    public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;
    /**
     * Allows buffer replacement using {@link ByteBuffer#allocate(int)}.
     */
    public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;
    /**
     * Allows buffer replacement using {@link ByteBuffer#allocateDirect(int)}.
     */
    public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;

    /**
     * {@link CryptoInfo} for encrypted data.
     */
    public final CryptoInfo cryptoInfo;

    /**
     * The buffer's data, or {@code null} if no data has been set.
     */
    public ByteBuffer data;

    /**
     * The time at which the sample should be presented.
     */
    public long timeUs;

    @BufferReplacementMode
    private final int bufferReplacementMode;

    /**
     * @param bufferReplacementMode Determines the behavior of {@link #ensureSpaceForWrite(int)}. One
     *                              of {@link #BUFFER_REPLACEMENT_MODE_DISABLED}, {@link #BUFFER_REPLACEMENT_MODE_NORMAL} and
     *                              {@link #BUFFER_REPLACEMENT_MODE_DIRECT}.
     */
    public DecoderInputBuffer(@BufferReplacementMode int bufferReplacementMode) {
        this.cryptoInfo = new CryptoInfo();
        this.bufferReplacementMode = bufferReplacementMode;
    }

    /**
     * Ensures that {@link #data} is large enough to accommodate a write of a given length at its
     * current position.
     * <p>
     * If the capacity of {@link #data} is sufficient this method does nothing. If the capacity is
     * insufficient then an attempt is made to replace {@link #data} with a new {@link ByteBuffer}
     * whose capacity is sufficient. Data up to the current position is copied to the new buffer.
     *
     * @param length The length of the write that must be accommodated, in bytes.
     * @throws IllegalStateException If there is insufficient capacity to accommodate the write and
     *                               the buffer replacement mode of the holder is {@link #BUFFER_REPLACEMENT_MODE_DISABLED}.
     */
    public void ensureSpaceForWrite(int length) throws IllegalStateException {
        if (data == null) {
            data = createReplacementByteBuffer(length);
            return;
        }
        // Check whether the current buffer is sufficient.
        int capacity = data.capacity();
        int position = data.position();
        int requiredCapacity = position + length;
        if (capacity >= requiredCapacity) {
            return;
        }
        // Instantiate a new buffer if possible.
        ByteBuffer newData = createReplacementByteBuffer(requiredCapacity);
        // Copy data up to the current position from the old buffer to the new one.
        if (position > 0) {
            data.position(0);
            data.limit(position);
            newData.put(data);
        }
        // Set the new buffer.
        data = newData;
    }

    /**
     * Returns whether the {@link C#BUFFER_FLAG_ENCRYPTED} flag is set.
     */
    public final boolean isEncrypted() {
        return getFlag(C.BUFFER_FLAG_ENCRYPTED);
    }

    /**
     * Flips {@link #data} in preparation for being queued to a decoder.
     *
     * @see java.nio.Buffer#flip()
     */
    public final void flip() {
        data.flip();
    }

    @Override
    public void clear() {
        super.clear();
        if (data != null) {
            data.clear();
        }
    }

    private ByteBuffer createReplacementByteBuffer(int requiredCapacity) {
        if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_NORMAL) {
            return ByteBuffer.allocate(requiredCapacity);
        } else if (bufferReplacementMode == BUFFER_REPLACEMENT_MODE_DIRECT) {
            return ByteBuffer.allocateDirect(requiredCapacity);
        } else {
            int currentCapacity = data == null ? 0 : data.capacity();
            throw new IllegalStateException("Buffer too small (" + currentCapacity + " < "
                    + requiredCapacity + ")");
        }
    }

}
