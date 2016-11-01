package com.yalin.exoplayer.decoder;

import com.yalin.exoplayer.C;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public abstract class Buffer {
    @C.BufferFlags
    private int flags;

    public void clear() {
        flags = 0;
    }

    public final boolean isDecodeOnly() {
        return getFlag(C.BUFFER_FLAG_DECODE_ONLY);
    }

    public final boolean isEndOfStream() {
        return getFlag(C.BUFFER_FLAG_END_OF_STREAM);
    }

    public final boolean isKeyFrame() {
        return getFlag(C.BUFFER_FLAG_KEY_FRAME);
    }

    public final void setFlags(@C.BufferFlags int flags) {
        this.flags = flags;
    }

    public final void addFlag(@C.BufferFlags int flag) {
        flags |= flag;
    }

    public final void clearFlag(@C.BufferFlags int flag) {
        flags &= ~flag;
    }

    protected final boolean getFlag(@C.BufferFlags int flag) {
        return (flags & flag) == flag;
    }

}
