package com.yalin.exoplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import com.yalin.exoplayer.drm.DrmSessionManager;
import com.yalin.exoplayer.drm.FrameworkMediaCrypto;
import com.yalin.exoplayer.source.MediaSource;
import com.yalin.exoplayer.text.TextRenderer;
import com.yalin.exoplayer.trackslection.TrackSelections;
import com.yalin.exoplayer.trackslection.TrackSelector;
import com.yalin.exoplayer.video.MediaCodecVideoRenderer;

import java.util.ArrayList;

/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */
@TargetApi(16)
public final class SimpleExoPlayer implements ExoPlayer {

    public interface VideoListener {
        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                float pixelWidthHeightRatio);

        void onRenderedFirstFrame();

        void onVideoTracksDisabled();
    }

    private static final String TAG = "SimpleExoPlayer";

    private final ExoPlayer player;
    private final Renderer[] renderers;
    private final Handler mainHandler;
    private final ComponentListener componentListener;
    private final int videoRendererCount;
    private final int audioRendererCount;

    private boolean videoTrackEnabled;
    private Format videoFormat;
    private Format audioFormat;

    private Surface surface;
    private boolean ownsSurface;
    private SurfaceHolder surfaceHolder;
    private TextureView textureView;
    private TextRenderer.Output textOutput;
    private VideoListener videoListener;
    private float volume;

    public SimpleExoPlayer(Context context, TrackSelector<?> trackSelector,
                           LoadControl loadControl, DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                           boolean preferExtensionDecoders, long allowedVideoJoiningTimeMs) {
        mainHandler = new Handler();
        componentListener = new ComponentListener();
        trackSelector.addListener(componentListener);

        ArrayList<Renderer> renderersList = new ArrayList<>();
        if (preferExtensionDecoders) {
            buildExtensionRenderers(renderersList);
            buildRenderers(context, drmSessionManager, renderersList, allowedVideoJoiningTimeMs);
        } else {
            buildRenderers(context, drmSessionManager, renderersList, allowedVideoJoiningTimeMs);
            buildExtensionRenderers(renderersList);
        }
        renderers = renderersList.toArray(new Renderer[renderersList.size()]);

        int videoRendererCount = 0;
        int audioRendererCount = 0;
        for (Renderer renderer : renderers) {
            switch (renderer.getTrackType()) {
                case C.TRACK_TYPE_VIDEO:
                    videoRendererCount++;
                    break;
                case C.TRACK_TYPE_AUDIO:
                    audioRendererCount++;
                    break;
            }
        }
        this.videoRendererCount = videoRendererCount;
        this.audioRendererCount = audioRendererCount;

        player = new ExoPlayerImpl(renderers, trackSelector, loadControl);
    }

    public int getRendererCount() {
        return renderers.length;
    }

    public int getRendererType(int index) {
        return renderers[index].getTrackType();
    }

    public void clearVideoSurface() {
        setVideoSurface(null);
    }

    public void setVideoSurface(Surface surface) {
        removeSurfaceCallbacks();
        setVideoSurfaceInternal(surface, false);
    }

    public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
        removeSurfaceCallbacks();
        this.surfaceHolder = surfaceHolder;
        if (surfaceHolder == null) {
            setVideoSurfaceInternal(null, false);
        } else {
            setVideoSurfaceInternal(surfaceHolder.getSurface(), false);
            surfaceHolder.addCallback(componentListener);
        }
    }

    public void setVideoSurfaceView(SurfaceView surfaceView) {
        setVideoSurfaceHolder(surfaceView.getHolder());
    }

    public void setVideoTextureView(TextureView textureView) {
        removeSurfaceCallbacks();
        this.textureView = textureView;
        if (textureView == null) {
            setVideoSurfaceInternal(null, true);
        } else {
            if (textureView.getSurfaceTextureListener() != null) {
                Log.w(TAG, "Replacing existing SurfaceTextureListener.");
            }
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            setVideoSurfaceInternal(surfaceTexture == null ? null : new Surface(surfaceTexture), true);
            textureView.setSurfaceTextureListener(componentListener);
        }
    }

    public void setVolume(float volume) {
        this.volume = volume;
        ExoPlayerMessage[] messages = new ExoPlayerMessage[audioRendererCount];
        int count = 0;
        for (Renderer renderer : renderers) {
            if (renderer.getTrackType() == C.TRACK_TYPE_AUDIO) {
                messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_VOLUME, volume);
            }
        }
        player.sendMessage(messages);
    }

    public float getVolume() {
        return volume;
    }

    public Format getVideoFormat() {
        return videoFormat;
    }

    public Format getAudioFormat() {
        return audioFormat;
    }

    public void setVideoListener(VideoListener listener) {
        videoListener = listener;
    }

    public void setTextOutput(TextRenderer.Output output) {
        textOutput = output;
    }

    @Override
    public void addListener(EventListener listener) {
        player.addListener(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        player.removeListener(listener);
    }

    @Override
    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    @Override
    public void prepare(MediaSource mediaSource) {
        player.prepare(mediaSource);
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetTimeline) {
        player.prepare(mediaSource, resetPosition, resetTimeline);
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        player.setPlayWhenReady(playWhenReady);
    }

    @Override
    public boolean getPlayWhenReady() {
        return player.getPlayWhenReady();
    }

    @Override
    public boolean isLoading() {
        return player.isLoading();
    }

    @Override
    public void seekToDefaultPosition() {
        player.seekToDefaultPosition();
    }

    @Override
    public void seekToDefaultPosition(int windowIndex) {
        player.seekToDefaultPosition(windowIndex);
    }

    @Override
    public void seekTo(long windowPositionMs) {
        player.seekTo(windowPositionMs);
    }

    @Override
    public void seekTo(int windowIndex, long windowPositionMs) {
        player.seekTo(windowIndex, windowPositionMs);
    }

    @Override
    public void stop() {
        player.stop();
    }

    @Override
    public void release() {
        player.release();
        removeSurfaceCallbacks();
        if (surface != null) {
            if (ownsSurface) {
                surface.release();
            }
            surface = null;
        }
    }

    @Override
    public void sendMessage(ExoPlayerMessage... messages) {
        player.sendMessage(messages);
    }

    @Override
    public void blockingSendMessage(ExoPlayerMessage... messages) {
        player.blockingSendMessage(messages);
    }

    @Override
    public Object getCurrentManifest() {
        return player.getCurrentManifest();
    }

    @Override
    public Timeline getCurrentTimeline() {
        return player.getCurrentTimeline();
    }

    @Override
    public int getCurrentPeriodIndex() {
        return player.getCurrentPeriodIndex();
    }

    @Override
    public int getCurrentWindowIndex() {
        return player.getCurrentWindowIndex();
    }

    @Override
    public long getDuration() {
        return player.getDuration();
    }

    @Override
    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    @Override
    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    @Override
    public int getBufferedPercentage() {
        return player.getBufferedPercentage();
    }

    private void buildExtensionRenderers(ArrayList<Renderer> renderersList) {

    }

    private void buildRenderers(Context context,
                                DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                                ArrayList<Renderer> renderersList, long allowedVideoJoiningTimeMs) {
        MediaCodecVideoRenderer videoRenderer = new MediaCodecVideoRenderer();
        renderersList.add(videoRenderer);
    }

    private void removeSurfaceCallbacks() {
        if (textureView != null) {
            if (textureView.getSurfaceTextureListener() != componentListener) {
                Log.w(TAG, "SurfaceTextureListener already unset or replaced.");
            } else {
                textureView.setSurfaceTextureListener(null);
            }
            textureView = null;
        }
        if (surfaceHolder != null) {
            surfaceHolder.removeCallback(componentListener);
            surfaceHolder = null;
        }
    }

    private void setVideoSurfaceInternal(Surface surface, boolean ownSurface) {
        ExoPlayerMessage[] messages = new ExoPlayerMessage[videoRendererCount];
        int count = 0;
        for (Renderer renderer : renderers) {
            if (renderer.getTrackType() == C.TRACK_TYPE_VIDEO) {
                messages[count++] = new ExoPlayerMessage(renderer, C.MSG_SET_SURFACE, surface);
            }
        }
        if (this.surface != null && this.surface != surface) {
            if (this.ownsSurface) {
                this.surface.release();
            }
            player.blockingSendMessage(messages);
        } else {
            player.sendMessage(messages);
        }
        this.surface = surface;
        this.ownsSurface = ownSurface;
    }

    private final class ComponentListener implements TrackSelector.EventListener<Object>,
            SurfaceHolder.Callback, TextureView.SurfaceTextureListener {

        @Override
        public void onTrackSelectionsChanged(TrackSelections<?> trackSelections) {
            boolean videoTracksEnabled = false;
            for (int i = 0; i < renderers.length; i++) {
                if (renderers[i].getTrackType() == C.TRACK_TYPE_VIDEO && trackSelections.get(i) != null) {
                    videoTracksEnabled = true;
                    break;
                }
            }
            if (videoListener != null && SimpleExoPlayer.this.videoTrackEnabled && !videoTracksEnabled) {
                videoListener.onVideoTracksDisabled();
            }
            SimpleExoPlayer.this.videoTrackEnabled = videoTracksEnabled;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            setVideoSurfaceInternal(holder.getSurface(), false);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setVideoSurfaceInternal(new Surface(surface), true);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            setVideoSurfaceInternal(null, true);
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    }

}
