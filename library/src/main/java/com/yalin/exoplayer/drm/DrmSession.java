package com.yalin.exoplayer.drm;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public interface DrmSession<T extends ExoMediaCrypto> {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_ERROR, STATE_CLOSED, STATE_OPENING, STATE_OPENED, STATE_OPENED_WITH_KEYS})
    @interface State {
    }

    int STATE_ERROR = 0;

    int STATE_CLOSED = 1;

    int STATE_OPENING = 2;

    int STATE_OPENED = 3;

    int STATE_OPENED_WITH_KEYS = 4;

    @State
    int getState();

    T getMediaCrypto();

    boolean requiresSecureDecoderComponent(String mimeType);

    Exception getError();
}
