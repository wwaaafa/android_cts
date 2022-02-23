/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.verifier.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.View;
import android.view.ViewGroup;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for media player shown in shade when media style notification is posted.
 */
public class MediaPlayerVerifierActivity extends InteractiveVerifierActivity {

    // Media session info
    private static final String SESSION_KEY = "Session";
    private static final String SESSION_TITLE = "Song";
    private static final String SESSION_ARTIST = "Artist";
    private static final long SESSION_DURATION = 60000L;

    // MediaStyle notification info
    private static final String TITLE = "Media-style Notification";
    private static final String TEXT = "Notification for a test media session";
    private static final String CHANNEL_ID = "MediaPlayerVerifierActivity";

    private MediaSession mSession;
    private NotificationManager mManager;
    private Notification.Builder mBuilder;

    @Override
    public List<InteractiveTestCase> createTestItems() {
        List<InteractiveTestCase> cases = new ArrayList<>();
        cases.add(new MediaPlayerTestCase(R.string.media_controls_visible));
        cases.add(new MediaPlayerTestCase(R.string.media_controls_output_switcher_chip));
        return cases;
    }

    @Override
    public int getInstructionsResource() {
        return R.string.media_controls_info;
    }

    @Override
    public int getTitleResource() {
        return R.string.media_controls_title;
    }

    private class MediaPlayerTestCase extends InteractiveTestCase {
        private final int mDescriptionResId;

        MediaPlayerTestCase(int resId) {
            mDescriptionResId = resId;
        }

        @Override
        protected void setUp() {
            postMediaStyleNotification();
            status = READY;
        }

        @Override
        protected void tearDown() {
            cancelMediaStyleNotification();
        }

        @Override
        protected View inflate(ViewGroup parent) {
            return createPassFailItem(parent, mDescriptionResId);
        }

        @Override
        protected void test() {
            status = WAIT_FOR_USER;
            next();
        }
    }

    private void postMediaStyleNotification() {
        mManager = this.getSystemService(NotificationManager.class);
        mSession = new MediaSession(this, SESSION_KEY);

        // Create a solid color bitmap to use as album art in media metadata
        Bitmap bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawColor(Color.GREEN);

        // Set up media session with metadata and playback state
        mSession.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_ARTIST, SESSION_ARTIST)
                .putString(MediaMetadata.METADATA_KEY_TITLE, SESSION_TITLE)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, SESSION_DURATION)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                .build());
        mSession.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 6000L, 1f)
                .setActions(PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SKIP_TO_NEXT)
                .addCustomAction("rewind", "rewind", android.R.drawable.ic_media_rew)
                .addCustomAction("fast forward", "fast forward", android.R.drawable.ic_media_ff)
                .build());

        // Set up notification builder
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW);
        mManager.createNotificationChannel(channel);
        mBuilder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(TITLE).setContentText(TEXT)
                .setSmallIcon(R.drawable.ic_android)
                .setStyle(new Notification.MediaStyle()
                        .setShowActionsInCompactView(1, 2, 3)
                        .setMediaSession(mSession.getSessionToken()))
                .setColor(Color.BLUE)
                .setColorized(true)
                .addAction(android.R.drawable.ic_media_rew, "rewind", null)
                .addAction(android.R.drawable.ic_media_previous, "previous track", null)
                .addAction(android.R.drawable.ic_media_play, "play", null)
                .addAction(android.R.drawable.ic_media_next, "next track", null)
                .addAction(android.R.drawable.ic_media_ff, "fast forward", null);

        mSession.setActive(true);
        mManager.notify(1, mBuilder.build());
    }

    private void cancelMediaStyleNotification() {
        if (mSession != null) {
            mSession.release();
            mSession = null;
        }
        if (mManager != null) {
            mManager.cancelAll();
            mManager.deleteNotificationChannel(CHANNEL_ID);
            mManager = null;
        }
    }
}
