package com.yalin.exoplayer.demo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.DefaultLoadControl;
import com.yalin.exoplayer.ExoPlaybackException;
import com.yalin.exoplayer.ExoPlayer;
import com.yalin.exoplayer.ExoPlayerFactory;
import com.yalin.exoplayer.SimpleExoPlayer;
import com.yalin.exoplayer.Timeline;
import com.yalin.exoplayer.drm.DrmSessionManager;
import com.yalin.exoplayer.drm.FrameworkMediaCrypto;
import com.yalin.exoplayer.extractor.DefaultExtractorsFactory;
import com.yalin.exoplayer.source.ConcatenatingMediaSource;
import com.yalin.exoplayer.source.ExtractorMediaSource;
import com.yalin.exoplayer.source.MediaSource;
import com.yalin.exoplayer.source.smoothstreaming.DefaultSsChunkSource;
import com.yalin.exoplayer.source.smoothstreaming.SsMediaSource;
import com.yalin.exoplayer.trackslection.AdaptiveVideoTrackSelection;
import com.yalin.exoplayer.trackslection.DefaultTrackSelector;
import com.yalin.exoplayer.trackslection.MappingTrackSelector;
import com.yalin.exoplayer.trackslection.MappingTrackSelector.MappedTrackInfo;
import com.yalin.exoplayer.trackslection.TrackSelection;
import com.yalin.exoplayer.trackslection.TrackSelections;
import com.yalin.exoplayer.trackslection.TrackSelector;
import com.yalin.exoplayer.ui.PlaybackControlView;
import com.yalin.exoplayer.ui.SimpleExoPlayerView;
import com.yalin.exoplayer.upstream.DataSource;
import com.yalin.exoplayer.upstream.DefaultBandwidthMeter;
import com.yalin.exoplayer.util.Util;


/**
 * 作者：YaLin
 * 日期：2016/10/31.
 */

public class PlayerActivity extends AppCompatActivity implements PlaybackControlView.VisibilityListener
        , ExoPlayer.EventListener, TrackSelector.EventListener<MappedTrackInfo> {
    private SimpleExoPlayerView simpleExoPlayerView;
    private Timeline.Window window;
    private Handler mainHandler;
    private EventLogger eventLogger;

    private SimpleExoPlayer player;
    private MappingTrackSelector trackSelector;

    private DataSource.Factory mediaDataSourceFactory;
    private boolean playerNeedsSource;

    private boolean shouldAutoPlay;
    private boolean isTimelineStatic;
    private int playerWindow;
    private long playerPosition;

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shouldAutoPlay = true;
        mediaDataSourceFactory = buildDataSourceFactory(true);
        mainHandler = new Handler();
        window = new Timeline.Window();
        setContentView(R.layout.activity_player);

        simpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
        simpleExoPlayerView.setControllerVisibilityListener(this);
        simpleExoPlayerView.requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        releasePlayer();
        isTimelineStatic = false;
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializePlayer();
        } else {
            showToast(R.string.storage_permission_denied);
            finish();
        }
    }

    @Override
    public void onVisibilityChange(int visibility) {

    }

    private void initializePlayer() {
        Intent intent = getIntent();
        if (player == null) {
            boolean preferExtensionDecoders = true;
            DrmSessionManager<FrameworkMediaCrypto> drmSessionManager = null;

            eventLogger = new EventLogger();
            TrackSelection.Factory videoTrackSelectionFactory =
                    new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);
            trackSelector = new DefaultTrackSelector(mainHandler, videoTrackSelectionFactory);
            trackSelector.addListener(this);
            trackSelector.addListener(eventLogger);
            player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, new DefaultLoadControl(),
                    drmSessionManager, preferExtensionDecoders);
            player.addListener(this);
            simpleExoPlayerView.setPlayer(player);
            player.setPlayWhenReady(shouldAutoPlay);
            player.addListener(eventLogger);
            player.setAudioDebugListener(eventLogger);
            player.setVideoDebugListener(eventLogger);
            player.setId3Output(eventLogger);
            if (isTimelineStatic) {
                if (playerPosition == C.TIME_UNSET) {
                    player.seekToDefaultPosition(playerWindow);
                } else {
                    player.seekTo(playerWindow, playerPosition);
                }
            }
            playerNeedsSource = true;
        }
        if (playerNeedsSource) {
            Uri[] uris;
            String[] extensions;
            uris = new Uri[]{Uri.parse("http://html5demos.com/assets/dizzy.mp4")};
            extensions = new String[]{"xxx"};
            if (Util.maybeRequestReadExternalStoragePermission(this, uris)) {
                return;
            }
            MediaSource[] mediaSources = new MediaSource[uris.length];
            for (int i = 0; i < uris.length; i++) {
                mediaSources[i] = buildMediaSource(uris[i], extensions[i]);
            }
            MediaSource mediaSource = mediaSources.length == 1 ? mediaSources[0]
                    : new ConcatenatingMediaSource(mediaSources);
            player.prepare(mediaSource, !isTimelineStatic, !isTimelineStatic);
            playerNeedsSource = false;
        }
    }

    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
//        return new SsMediaSource(uri, buildDataSourceFactory(false),
//                new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
        return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                mainHandler, null);
    }

    private DataSource.Factory buildDataSourceFactory(boolean useBandWidthMeter) {
        return ((DemoApplication) getApplication())
                .buildDataSourceFactory(useBandWidthMeter ? BANDWIDTH_METER : null);
    }

    private void releasePlayer() {
        if (player != null) {
            shouldAutoPlay = player.getPlayWhenReady();
            playerWindow = player.getCurrentWindowIndex();
            playerPosition = C.TIME_UNSET;
            Timeline timeline = player.getCurrentTimeline();
            if (timeline != null && timeline.getWindow(playerWindow, window).isSeekable) {
                playerPosition = player.getCurrentPosition();
            }
            player.release();
            player = null;
        }
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        isTimelineStatic = timeline != null && timeline.getWindowCount() > 0
                && !timeline.getWindow(timeline.getWindowCount() - 1, window).isDynamic;
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    @Override
    public void onPositionDiscontinuity() {

    }

    private void showToast(int messageId) {
        showToast(getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onTrackSelectionsChanged(TrackSelections trackSelections) {

    }
}
