package com.yalin.exoplayer.text;

import android.os.Handler;
import android.os.Message;

import com.yalin.exoplayer.BaseRenderer;
import com.yalin.exoplayer.ExoPlaybackException;
import com.yalin.exoplayer.Format;

import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public final class TextRenderer extends BaseRenderer implements Handler.Callback {
    public interface Output {
        void onCues(List<Cue> cues);
    }

    @Override
    public boolean handleMessage(Message msg) {
        return false;
    }

    @Override
    public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {

    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public boolean isEnded() {
        return false;
    }

    @Override
    public int supportsFormat(Format format) throws ExoPlaybackException {
        return 0;
    }
}
