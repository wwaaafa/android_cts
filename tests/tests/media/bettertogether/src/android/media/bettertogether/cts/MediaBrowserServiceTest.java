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
package android.media.bettertogether.cts;

import static android.media.bettertogether.cts.MediaBrowserServiceTestService.KEY_PARENT_MEDIA_ID;
import static android.media.bettertogether.cts.MediaBrowserServiceTestService.KEY_SERVICE_COMPONENT_NAME;
import static android.media.bettertogether.cts.MediaBrowserServiceTestService.TEST_SERIES_OF_NOTIFY_CHILDREN_CHANGED;
import static android.media.bettertogether.cts.MediaSessionTestService.KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS;
import static android.media.bettertogether.cts.MediaSessionTestService.STEP_CHECK;
import static android.media.bettertogether.cts.MediaSessionTestService.STEP_CLEAN_UP;
import static android.media.bettertogether.cts.MediaSessionTestService.STEP_SET_UP;
import static android.media.browse.MediaBrowser.MediaItem.FLAG_PLAYABLE;
import static android.media.cts.Utils.compareRemoteUserInfo;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSessionManager.RemoteUserInfo;
import android.os.Bundle;
import android.os.Process;
import android.service.media.MediaBrowserService;
import android.service.media.MediaBrowserService.BrowserRoot;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link android.service.media.MediaBrowserService}.
 */
@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class MediaBrowserServiceTest {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;
    private static final long WAIT_TIME_FOR_NO_RESPONSE_MS = 500L;
    private static final ComponentName TEST_BROWSER_SERVICE = new ComponentName(
            "android.media.bettertogether.cts",
            "android.media.bettertogether.cts.StubMediaBrowserService");

    private final TestCountDownLatch mOnChildrenLoadedLatch = new TestCountDownLatch();
    private final TestCountDownLatch mOnChildrenLoadedWithOptionsLatch = new TestCountDownLatch();
    private final TestCountDownLatch mOnItemLoadedLatch = new TestCountDownLatch();

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
            new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaItem> children) {
                if (children != null) {
                    for (MediaItem item : children) {
                        assertRootHints(item);
                    }
                }
                mOnChildrenLoadedLatch.countDown();
            }

            @Override
            public void onChildrenLoaded(String parentId, List<MediaItem> children,
                    Bundle options) {
                if (children != null) {
                    for (MediaItem item : children) {
                        assertRootHints(item);
                    }
                }
                mOnChildrenLoadedWithOptionsLatch.countDown();
            }
        };

    private final MediaBrowser.ItemCallback mItemCallback = new MediaBrowser.ItemCallback() {
        @Override
        public void onItemLoaded(MediaItem item) {
            assertRootHints(item);
            mOnItemLoadedLatch.countDown();
        }
    };

    private MediaBrowser mMediaBrowser;
    private RemoteUserInfo mBrowserInfo;
    private StubMediaBrowserService mMediaBrowserService;
    private Bundle mRootHints;

    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    @Before
    public void setUp() throws Exception {
        mRootHints = new Bundle();
        mRootHints.putBoolean(BrowserRoot.EXTRA_RECENT, true);
        mRootHints.putBoolean(BrowserRoot.EXTRA_OFFLINE, true);
        mRootHints.putBoolean(BrowserRoot.EXTRA_SUGGESTED, true);
        mBrowserInfo = new RemoteUserInfo(
                getInstrumentation().getTargetContext().getPackageName(),
                Process.myPid(),
                Process.myUid());
        mOnChildrenLoadedLatch.reset();
        mOnChildrenLoadedWithOptionsLatch.reset();
        mOnItemLoadedLatch.reset();

        final CountDownLatch onConnectedLatch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser = new MediaBrowser(getInstrumentation().getTargetContext(),
                TEST_BROWSER_SERVICE, new MediaBrowser.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        mMediaBrowserService = StubMediaBrowserService.sInstance;
                        onConnectedLatch.countDown();
                    }
                }, mRootHints);
            mMediaBrowser.connect();
        });
        assertThat(onConnectedLatch.await(TIME_OUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(mMediaBrowserService).isNotNull();
    }

    @After
    public void tearDown() {
        getInstrumentation().runOnMainSync(()-> {
            if (mMediaBrowser != null) {
                mMediaBrowser.disconnect();
                mMediaBrowser = null;
            }
        });
    }

    @Test
    public void testGetSessionToken() {
        assertThat(mMediaBrowserService.getSessionToken())
                .isEqualTo(StubMediaBrowserService.sSession.getSessionToken());
    }

    @Test
    public void testNotifyChildrenChanged() throws Exception {
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
        });
        assertThat(mOnChildrenLoadedLatch.await(TIME_OUT_MS)).isTrue();

        mOnChildrenLoadedLatch.reset();
        mMediaBrowserService.notifyChildrenChanged(StubMediaBrowserService.MEDIA_ID_ROOT);
        assertThat(mOnChildrenLoadedLatch.await(TIME_OUT_MS)).isTrue();
    }

    @Test
    public void testNotifyChildrenChangedWithNullOptionsThrowsIAE() {
        assertThrows(IllegalArgumentException.class,
                () -> mMediaBrowserService.notifyChildrenChanged(
                        StubMediaBrowserService.MEDIA_ID_ROOT, /*options=*/ null));
    }

    @Test
    public void testNotifyChildrenChangedWithPagination() {
        final int pageSize = 5;
        final int page = 2;
        Bundle options = new Bundle();
        options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
        options.putInt(MediaBrowser.EXTRA_PAGE, page);

        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, options,
                    mSubscriptionCallback);
        });
        assertThat(mOnChildrenLoadedWithOptionsLatch.await(TIME_OUT_MS)).isTrue();

        mOnChildrenLoadedWithOptionsLatch.reset();
        mMediaBrowserService.notifyChildrenChanged(StubMediaBrowserService.MEDIA_ID_ROOT);
        assertThat(mOnChildrenLoadedWithOptionsLatch.await(TIME_OUT_MS)).isTrue();

        // Notify that the items overlapping with the given options are changed.
        mOnChildrenLoadedWithOptionsLatch.reset();
        final int newPageSize = 3;
        final int overlappingNewPage = pageSize * page / newPageSize;
        Bundle overlappingOptions = new Bundle();
        overlappingOptions.putInt(MediaBrowser.EXTRA_PAGE_SIZE, newPageSize);
        overlappingOptions.putInt(MediaBrowser.EXTRA_PAGE, overlappingNewPage);
        mMediaBrowserService.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT, overlappingOptions);
        assertThat(mOnChildrenLoadedWithOptionsLatch.await(TIME_OUT_MS)).isTrue();

        // Notify that the items non-overlapping with the given options are changed.
        mOnChildrenLoadedWithOptionsLatch.reset();
        Bundle nonOverlappingOptions = new Bundle();
        nonOverlappingOptions.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
        nonOverlappingOptions.putInt(MediaBrowser.EXTRA_PAGE, page + 1);
        mMediaBrowserService.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT, nonOverlappingOptions);
        assertThat(mOnChildrenLoadedWithOptionsLatch.await(WAIT_TIME_FOR_NO_RESPONSE_MS)).isFalse();
    }

    @Test
    public void testDelayedNotifyChildrenChanged() throws Exception {
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED,
                    mSubscriptionCallback);
        });
        assertThat(mOnChildrenLoadedLatch.await(WAIT_TIME_FOR_NO_RESPONSE_MS)).isFalse();

        mMediaBrowserService.sendDelayedNotifyChildrenChanged();
        assertThat(mOnChildrenLoadedLatch.await(TIME_OUT_MS)).isTrue();

        mOnChildrenLoadedLatch.reset();
        mMediaBrowserService.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED);
        assertThat(mOnChildrenLoadedLatch.await(WAIT_TIME_FOR_NO_RESPONSE_MS)).isFalse();

        mMediaBrowserService.sendDelayedNotifyChildrenChanged();
        assertThat(mOnChildrenLoadedLatch.await(TIME_OUT_MS)).isTrue();
    }

    @Test
    public void testDelayedItem() throws Exception {
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED,
                    mItemCallback);
        });
        assertThat(mOnItemLoadedLatch.await(WAIT_TIME_FOR_NO_RESPONSE_MS)).isFalse();

        mMediaBrowserService.sendDelayedItemLoaded();
        assertThat(mOnItemLoadedLatch.await(TIME_OUT_MS)).isTrue();
    }

    @Test
    public void testGetBrowserInfo() throws Exception {
        // StubMediaBrowserService stores the browser info in its onGetRoot().
        assertThat(compareRemoteUserInfo(mBrowserInfo, StubMediaBrowserService.sBrowserInfo))
                .isTrue();

        StubMediaBrowserService.clearBrowserInfo();
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
        });
        assertThat(mOnChildrenLoadedLatch.await(TIME_OUT_MS)).isTrue();
        assertThat(compareRemoteUserInfo(mBrowserInfo, StubMediaBrowserService.sBrowserInfo))
                .isTrue();

        StubMediaBrowserService.clearBrowserInfo();
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0], mItemCallback);
        });
        assertThat(mOnItemLoadedLatch.await(TIME_OUT_MS)).isTrue();
        assertThat(compareRemoteUserInfo(mBrowserInfo, StubMediaBrowserService.sBrowserInfo))
                .isTrue();
    }

    @Test
    public void testBrowserRoot() {
        final String id = "test-id";
        final String key = "test-key";
        final String val = "test-val";
        final Bundle extras = new Bundle();
        extras.putString(key, val);

        MediaBrowserService.BrowserRoot browserRoot = new BrowserRoot(id, extras);
        assertThat(browserRoot.getRootId()).isEqualTo(id);
        assertThat(browserRoot.getExtras().getString(key)).isEqualTo(val);
    }

    /**
     * Check that a series of {@link MediaBrowserService#notifyChildrenChanged} does not break
     * {@link MediaBrowser} on the remote process due to binder buffer overflow.
     */
    @Test
    public void testSeriesOfNotifyChildrenChanged() throws Exception {
        String parentMediaId = "testSeriesOfNotifyChildrenChanged";
        int numberOfCalls = 100;
        int childrenSize = 1_000;
        List<MediaItem> children = new ArrayList<>();
        for (int id = 0; id < childrenSize; id++) {
            MediaDescription description = new MediaDescription.Builder()
                    .setMediaId(Integer.toString(id)).build();
            children.add(new MediaItem(description, FLAG_PLAYABLE));
        }
        mMediaBrowserService.putChildrenToMap(parentMediaId, children);

        try (RemoteService.Invoker invoker = new RemoteService.Invoker(
                ApplicationProvider.getApplicationContext(),
                MediaBrowserServiceTestService.class,
                TEST_SERIES_OF_NOTIFY_CHILDREN_CHANGED)) {
            Bundle args = new Bundle();
            args.putParcelable(KEY_SERVICE_COMPONENT_NAME, TEST_BROWSER_SERVICE);
            args.putString(KEY_PARENT_MEDIA_ID, parentMediaId);
            args.putInt(KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS, numberOfCalls * childrenSize);
            invoker.run(STEP_SET_UP, args);
            for (int i = 0; i < numberOfCalls; i++) {
                mMediaBrowserService.notifyChildrenChanged(parentMediaId);
            }
            invoker.run(STEP_CHECK);
            invoker.run(STEP_CLEAN_UP);
        }

        mMediaBrowserService.removeChildrenFromMap(parentMediaId);
    }

    private void assertRootHints(MediaItem item) {
        Bundle rootHints = item.getDescription().getExtras();
        assertThat(rootHints).isNotNull();
        assertThat(rootHints.getBoolean(BrowserRoot.EXTRA_RECENT))
                .isEqualTo(mRootHints.getBoolean(BrowserRoot.EXTRA_RECENT));
        assertThat(rootHints.getBoolean(BrowserRoot.EXTRA_OFFLINE))
                .isEqualTo(mRootHints.getBoolean(BrowserRoot.EXTRA_OFFLINE));
        assertThat(rootHints.getBoolean(BrowserRoot.EXTRA_SUGGESTED))
                .isEqualTo(mRootHints.getBoolean(BrowserRoot.EXTRA_SUGGESTED));
    }

    private static class TestCountDownLatch {
        private CountDownLatch mLatch;

        TestCountDownLatch() {
            mLatch = new CountDownLatch(1);
        }

        void reset() {
            mLatch = new CountDownLatch(1);
        }

        void countDown() {
            mLatch.countDown();
        }

        boolean await(long timeoutMs) {
            try {
                return mLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }
}
