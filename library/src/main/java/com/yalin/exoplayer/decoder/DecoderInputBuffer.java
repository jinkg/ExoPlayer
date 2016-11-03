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

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BUFFER_REPLACEMENT_MODE_DISABLED, BUFFER_REPLACEMENT_MODE_NORMAL
            , BUFFER_REPLACEMENT_MODE_DIRECT})
    public @interface BufferReplacementMode {
    }

    public static final int BUFFER_REPLACEMENT_MODE_DISABLED = 0;

    public static final int BUFFER_REPLACEMENT_MODE_NORMAL = 1;

    public static final int BUFFER_REPLACEMENT_MODE_DIRECT = 2;

    public final CryptoInfo cryptoInfo;

    public ByteBuffer data;

    public long timeUs;

    @BufferReplacementMode
    private final int bufferReplacementMode;

    public DecoderInputBuffer(@BufferReplacementMode int bufferReplacementMode) {
        this.cryptoInfo = new CryptoInfo();
        this.bufferReplacementMode = bufferReplacementMode;
    }

    public final boolean isEncrypted() {
        return getFlag(C.BUFFER_FLAG_ENCRYPTED);
    }

    public void ensureSpaceForWrite(int length) throws IllegalStateException {

    }

    public final void flip() {
        data.flip();
    }
}
