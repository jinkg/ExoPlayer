package com.yalin.exoplayer;

import java.io.IOException;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class ExoPlaybackException extends Exception {

    public static ExoPlaybackException createForSource(IOException cause) {
        return new ExoPlaybackException();
    }

    public static ExoPlaybackException createForUnexpected(RuntimeException cause) {
        return new ExoPlaybackException();
    }

    public static ExoPlaybackException createForRenderer(Exception cause,int rendererIndex){
        return new ExoPlaybackException();
    }
}
