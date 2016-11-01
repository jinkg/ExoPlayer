package com.yalin.exoplayer.drm;

import android.os.Looper;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface DrmSessionManager<T extends ExoMediaCrypto> {
    DrmSession<T> acquireSession(Looper playbackLooper, DrmInitData drmInitData);

    void releaseSession(DrmSession<T> drmSession);
}
