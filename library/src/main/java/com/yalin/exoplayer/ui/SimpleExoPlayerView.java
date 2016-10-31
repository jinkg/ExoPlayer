package com.yalin.exoplayer.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.yalin.exoplayer.SimpleExoPlayer;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class SimpleExoPlayerView extends FrameLayout {
    public SimpleExoPlayerView(Context context) {
        super(context);
    }

    public SimpleExoPlayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SimpleExoPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setControllerVisibilityListener(PlaybackControlView.VisibilityListener listener) {

    }

    public void setPlayer(SimpleExoPlayer player) {

    }
}
