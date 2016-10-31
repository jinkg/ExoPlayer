package com.yalin.exoplayer.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class PlaybackControlView extends FrameLayout {
    public interface VisibilityListener {
        void onVisibilityChange(int visibility);
    }

    public PlaybackControlView(Context context) {
        super(context);
    }

    public PlaybackControlView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PlaybackControlView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
