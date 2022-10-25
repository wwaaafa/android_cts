/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.photopicker.cts;

import static android.photopicker.cts.util.GetContentActivityAliasUtils.clearPackageData;
import static android.photopicker.cts.util.GetContentActivityAliasUtils.getDocumentsUiPackageName;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createDNGVideosAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createVideosAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.clickAndWait;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddOrSelectButton;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertContainsMimeType;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertMimeType;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertPersistedGrant;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertPickerUriFormat;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertRedactedReadOnlyAccess;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ClipData;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.photopicker.cts.util.GetContentActivityAliasUtils;
import android.provider.MediaStore;

import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Photo Picker Device only tests for common flows.
 */
@RunWith(Parameterized.class)
public class PhotoPickerTest extends PhotoPickerBaseTest {

    @Parameter(0)
    public String mAction;

    @Parameters(name = "intent={0}")
    public static Iterable<? extends Object> data() {
        return getTestParameters();
    }

    private List<Uri> mUriList = new ArrayList<>();

    private static int sGetContentTakeOverActivityAliasState;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        sGetContentTakeOverActivityAliasState = GetContentActivityAliasUtils.enableAndGetOldState();
        clearPackageData(getDocumentsUiPackageName());
    }

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();

        if (mActivity != null) {
            mActivity.finish();
        }

        GetContentActivityAliasUtils.restoreState(sGetContentTakeOverActivityAliasState);
    }

    @Test
    public void testSingleSelect() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        final UiObject item = findItemList(itemCount).get(0);
        clickAndWait(mDevice, item);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertPersistedGrant(uri, mContext.getContentResolver());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testSingleSelectForFavoritesAlbum() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId(),
                /* isFavorite */ true));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        UiObject albumsTab = mDevice.findObject(new UiSelector().text(
                "Albums"));
        clickAndWait(mDevice, albumsTab);
        final UiObject album = findItemList(1).get(0);
        clickAndWait(mDevice, album);

        final UiObject item = findItemList(itemCount).get(0);
        clickAndWait(mDevice, item);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testLaunchPreviewMultipleForVideoAlbum() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createVideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        UiObject albumsTab = mDevice.findObject(new UiSelector().text(
                "Albums"));
        clickAndWait(mDevice, albumsTab);
        final UiObject album = findItemList(1).get(0);
        clickAndWait(mDevice, album);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();

        assertThat(itemCount).isEqualTo(videoCount);

        for (int i = 0; i < itemCount; i++) {
            clickAndWait(mDevice, itemList.get(i));
        }

        clickAndWait(mDevice, findViewSelectedButton());

        // Wait for playback to start. This is needed in some devices where playback
        // buffering -> ready state takes around 10s.
        final long playbackStartTimeout = 10000;
        (findPreviewVideoImageView()).waitUntilGone(playbackStartTimeout);
    }

    @Test
    public void testSingleSelectWithPreview() throws Exception {
        final int itemCount = 1;
        mUriList.addAll(createImagesAndGetUris(itemCount, mContext.getUserId()));

        final Intent intent = new Intent(mAction);
        launchPhotoPickerForIntent(intent);

        final UiObject item = findItemList(itemCount).get(0);
        item.longClick();
        mDevice.waitForIdle();

        final UiObject addButton = findPreviewAddOrSelectButton();
        assertThat(addButton.waitForExists(1000)).isTrue();
        clickAndWait(mDevice, addButton);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect() throws Exception {
        final int imageCount = 4;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));
        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(mDevice, itemList.get(i));
        }

        clickAndWait(mDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_longPress() throws Exception {
        final int videoCount = 3;
        mUriList.addAll(createDNGVideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(videoCount);

        // Select one item from Photo grid
        clickAndWait(mDevice, itemList.get(0));

        // Preview the item
        UiObject item = itemList.get(1);
        item.longClick();
        mDevice.waitForIdle();

        final UiObject addOrSelectButton = findPreviewAddOrSelectButton();
        assertWithMessage("Timed out waiting for AddOrSelectButton to appear")
                .that(addOrSelectButton.waitForExists(1000)).isTrue();

        // Select the item from Preview
        clickAndWait(mDevice, addOrSelectButton);

        mDevice.pressBack();

        // Select one more item from Photo grid
        clickAndWait(mDevice, itemList.get(2));

        clickAndWait(mDevice, findAddButton());

        // Verify that all 3 items are returned
        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(3);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_preview() throws Exception {
        final int imageCount = 4;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(mDevice, itemList.get(i));
        }

        clickAndWait(mDevice, findViewSelectedButton());

        // Swipe left three times
        swipeLeftAndWait();
        swipeLeftAndWait();
        swipeLeftAndWait();

        // Deselect one item
        clickAndWait(mDevice, findPreviewSelectedCheckButton());

        // Return selected items
        clickAndWait(mDevice, findPreviewAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount - 1);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    @Ignore("Re-enable once we find work around for b/226318844")
    public void testMultiSelect_previewVideoPlayPause() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        // Check Play/Pause in first video
        testVideoPreviewPlayPause();

        // Move to third video
        swipeLeftAndWait();
        swipeLeftAndWait();
        // Check Play/Pause in third video
        testVideoPreviewPlayPause();

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMultiSelect_previewVideoMuteButtonInitial() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 1);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Initial state of the mute Button
        // Check that initial state of mute button is mute, i.e., volume off
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Click Mute Button
        // Click to unmute the audio
        clickAndWait(mDevice, muteButton);

        waitForBinderCallsToComplete();

        // Check that mute button state is unmute, i.e., it shows `volume up` icon
        assertMuteButtonState(muteButton, /* isMuted */ false);
        // Click on the muteButton and check that mute button status is now 'mute'
        clickAndWait(mDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Click on the muteButton and check that mute button status is now unmute
        clickAndWait(mDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ false);

        // Test 3: Next preview resumes mute state
        // Go back and launch preview again
        mDevice.pressBack();
        clickAndWait(mDevice, findViewSelectedButton());

        waitForBinderCallsToComplete();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMultiSelect_previewVideoMuteButtonOnSwipe() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();
        final UiObject playerView = findPlayerView();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Swipe resumes mute state, with state of the button is 'volume off' / 'mute'
        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Swipe to next page and check that muteButton is in mute state.
        swipeLeftAndWait();

        waitForBinderCallsToComplete();

        // set-up and wait for player controls to be sticky
        setUpAndAssertStickyPlayerControls(playerView, playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Swipe resumes mute state, with state of mute button 'volume up' / 'unmute'
        // Click muteButton again to check the next video resumes the previous video's mute state
        clickAndWait(mDevice, muteButton);

        waitForBinderCallsToComplete();

        assertMuteButtonState(muteButton, /* isMuted */ false);
        // check that next video resumed previous video's mute state
        swipeLeftAndWait();

        waitForBinderCallsToComplete();

        // Wait for 1s before checking Play/Pause button's visibility
        playPauseButton.waitForExists(1000);
        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testVideoPreviewAudioFocus() throws Exception {
        final int[] focusStateForTest = new int[1];
        final AudioManager audioManager = mContext.getSystemService(AudioManager.class);
        AudioFocusRequest audioFocusRequest =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.USAGE_MEDIA)
                        .setUsage(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build())
                .setWillPauseWhenDucked(true)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                            || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        focusStateForTest[0] = focusChange;
                    }
                })
                .build();

        // Request AudioFocus
        assertWithMessage("Expected requestAudioFocus result")
                .that(audioManager.requestAudioFocus(audioFocusRequest))
                .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        // Launch Preview
        launchPreviewMultipleWithVideos(/* videoCount */ 2);
        // Video preview launches in mute mode, hence, test's audio focus shouldn't be lost when
        // video preview starts
        assertThat(focusStateForTest[0]).isEqualTo(0);

        final UiObject muteButton = findMuteButton();
        // unmute the audio of video preview
        clickAndWait(mDevice, muteButton);

        // Remote video preview involves binder calls
        // Wait for Binder calls to complete and device to be idle
        MediaStore.waitForIdle(mContext.getContentResolver());
        mDevice.waitForIdle();

        assertMuteButtonState(muteButton, /* isMuted */ false);

        // Verify that test lost the audio focus because PhotoPicker has requested audio focus now.
        assertThat(focusStateForTest[0]).isEqualTo(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        // Reset the focusStateForTest to verify test loses audio focus when video preview is
        // launched with unmute state
        focusStateForTest[0] = 0;
        // Abandon the audio focus before requesting again. This is necessary to reduce test flakes
        audioManager.abandonAudioFocusRequest(audioFocusRequest);
        // Request AudioFocus from test again
        assertWithMessage("Expected requestAudioFocus result")
                .that(audioManager.requestAudioFocus(audioFocusRequest))
                        .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

        // Wait for PhotoPicker to lose Audio Focus
        findPlayButton().waitForExists(SHORT_TIMEOUT);
        // Test requesting audio focus will make PhotoPicker lose audio focus, Verify video is
        // paused when PhotoPicker loses audio focus.
        assertWithMessage("PlayPause button's content description")
                .that(findPlayPauseButton().getContentDescription())
                .isEqualTo("Play");

        // Swipe to next video and verify preview gains audio focus
        swipeLeftAndWait();
        findPauseButton().waitForExists(SHORT_TIMEOUT);
        // Video preview is now in unmute mode. Hence, PhotoPicker will request audio focus. Verify
        // that test lost the audio focus.
        assertThat(focusStateForTest[0]).isEqualTo(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    }

    @Test
    @Ignore("Re-enable once we find work around for b/226318844")
    public void testMultiSelect_previewVideoControlsVisibility() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();
        // Check that buttons auto hide.
        assertPlayerControlsAutoHide(playPauseButton, muteButton);

        final UiObject playerView = findPlayerView();
        // Click on StyledPlayerView to make the video controls visible
        clickAndWait(mDevice, playerView);
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Wait for 1s and check that controls are still visible
        assertPlayerControlsDontAutoHide(playPauseButton, muteButton);

        // Click on StyledPlayerView and check that controls are no longer visible. Don't click in
        // the center, clicking in the center may pause the video.
        playerView.clickBottomRight();
        mDevice.waitForIdle();
        assertPlayerControlsHidden(playPauseButton, muteButton);

        // Swipe left and check that controls are not visible
        swipeLeftAndWait();
        assertPlayerControlsHidden(playPauseButton, muteButton);

        // Click on the StyledPlayerView and check that controls appear
        clickAndWait(mDevice, playerView);
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Swipe left to check that controls are now visible on swipe
        swipeLeftAndWait();
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Check that the player controls are auto hidden in 1s
        assertPlayerControlsAutoHide(playPauseButton, muteButton);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMimeTypeFilter() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createDNGVideosAndGetUris(videoCount, mContext.getUserId()));
        final int imageCount = 1;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        final String mimeType = "video/dng";

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        intent.setType(mimeType);
        launchPhotoPickerForIntent(intent);

        // find all items
        final List<UiObject> itemList = findItemList(-1);
        final int itemCount = itemList.size();
        assertThat(itemCount).isAtLeast(videoCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(mDevice, itemList.get(i));
        }

        clickAndWait(mDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }

    @Test
    public void testExtraMimeTypeFilter() throws Exception {
        final int dngVideoCount = 2;
        // Creates 2 videos with mime type: "video/dng"
        mUriList.addAll(createDNGVideosAndGetUris(dngVideoCount, mContext.getUserId()));

        final int mp4VideoCount = 3;
        // Creates 3 videos with mime type: "video/mp4"
        mUriList.addAll(createVideosAndGetUris(mp4VideoCount, mContext.getUserId()));

        final int imageCount = 4;
        // Creates 4 images with mime type: "image/dng"
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);

        if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
            intent.setType("*/*");
        }
        final String[] mimeTypes = new String[]{"video/dng", "image/dng"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        launchPhotoPickerForIntent(intent);

        final int totalCount = dngVideoCount + imageCount;
        final List<UiObject> itemList = findItemList(totalCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isAtLeast(totalCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(mDevice, itemList.get(i));
        }

        clickAndWait(mDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        assertWithMessage("Expected number of items returned to be: " + itemCount)
                .that(clipData.getItemCount()).isEqualTo(itemCount);
        for (int i = 0; i < itemCount; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertContainsMimeType(uri, mimeTypes);
        }
    }

    @Test
    public void testMimeTypeFilterPriority() throws Exception {
        final int videoCount = 2;
        mUriList.addAll(createDNGVideosAndGetUris(videoCount, mContext.getUserId()));
        final int imageCount = 1;
        mUriList.addAll(createImagesAndGetUris(imageCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        addMultipleSelectionFlag(intent);
        // setType has lower priority than EXTRA_MIME_TYPES filters.
        intent.setType("image/*");
        final String mimeType = "video/dng";
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {mimeType});
        launchPhotoPickerForIntent(intent);

        // find all items
        final List<UiObject> itemList = findItemList(-1);
        final int itemCount = itemList.size();
        assertThat(itemCount).isAtLeast(videoCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(mDevice, itemList.get(i));
        }

        clickAndWait(mDevice, findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        assertWithMessage("Expected number of items returned to be: " + itemCount)
                .that(clipData.getItemCount()).isEqualTo(itemCount);
        for (int i = 0; i < itemCount; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }

    private void assertMuteButtonState(UiObject muteButton, boolean isMuted)
            throws UiObjectNotFoundException {
        // We use content description to assert the state of the mute button, there is no other way
        // to test this.
        final String expectedContentDescription = isMuted ? "Unmute video" : "Mute video";
        final String assertMessage =
                "Expected mute button content description to be " + expectedContentDescription;
        assertWithMessage(assertMessage).that(muteButton.getContentDescription())
                .isEqualTo(expectedContentDescription);
    }

    private void testVideoPreviewPlayPause() throws Exception {
        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();

        // Wait for buttons to auto hide.
        assertPlayerControlsAutoHide(playPauseButton, muteButton);

        // Click on StyledPlayerView to make the video controls visible
        clickAndWait(mDevice, findPlayerView());

        // PlayPause button is now pause button, click the button to pause the video.
        clickAndWait(mDevice, playPauseButton);

        // Wait for 1s and check that play button is not auto hidden
        assertPlayerControlsDontAutoHide(playPauseButton, muteButton);

        // PlayPause button is now play button, click the button to play the video.
        clickAndWait(mDevice, playPauseButton);
        // Check that pause button auto-hides in 1s.
        assertPlayerControlsAutoHide(playPauseButton, muteButton);
    }

    private void launchPreviewMultipleWithVideos(int videoCount) throws  Exception {
        mUriList.addAll(createVideosAndGetUris(videoCount, mContext.getUserId()));

        Intent intent = new Intent(mAction);
        intent.setType("video/*");
        addMultipleSelectionFlag(intent);
        launchPhotoPickerForIntent(intent);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();

        assertThat(itemCount).isEqualTo(videoCount);

        for (int i = 0; i < itemCount; i++) {
            clickAndWait(mDevice, itemList.get(i));
        }

        clickAndWait(mDevice, findViewSelectedButton());

        // Wait for playback to start. This is needed in some devices where playback
        // buffering -> ready state takes around 10s.
        final long playbackStartTimeout = 10000;
        (findPreviewVideoImageView()).waitUntilGone(playbackStartTimeout);

        waitForBinderCallsToComplete();
    }

    private void waitForBinderCallsToComplete() {
        // Wait for Binder calls to complete and device to be idle
        MediaStore.waitForIdle(mContext.getContentResolver());
        mDevice.waitForIdle();
    }

    private void setUpAndAssertStickyPlayerControls(UiObject playerView, UiObject playPauseButton,
            UiObject muteButton) throws Exception {
        // Wait for 1s for player view to exist
        playerView.waitForExists(1000);
        // Wait for 1s or Play/Pause button to hide
        playPauseButton.waitUntilGone(1000);
        // Click on StyledPlayerView to make the video controls visible
        clickAndWait(mDevice, playerView);
        assertPlayerControlsVisible(playPauseButton, muteButton);
    }

    private void assertPlayerControlsVisible(UiObject playPauseButton, UiObject muteButton) {
        assertVisible(playPauseButton, "Expected play/pause button to be visible");
        assertVisible(muteButton, "Expected mute button to be visible");
    }

    private void assertPlayerControlsHidden(UiObject playPauseButton, UiObject muteButton) {
        assertHidden(playPauseButton, "Expected play/pause button to be hidden");
        assertHidden(muteButton, "Expected mute button to be hidden");
    }

    private void assertPlayerControlsAutoHide(UiObject playPauseButton, UiObject muteButton) {
        // These buttons should auto hide in 1 second after the video playback start. Since we can't
        // identify the video playback start time, we wait for 2 seconds instead.
        assertWithMessage("Expected play/pause button to auto hide in 2s")
                .that(playPauseButton.waitUntilGone(2000)).isTrue();
        assertHidden(muteButton, "Expected mute button to hide after 2s");
    }

    private void assertPlayerControlsDontAutoHide(UiObject playPauseButton, UiObject muteButton) {
        assertWithMessage("Expected play/pause button to not auto hide in 1s")
                .that(playPauseButton.waitUntilGone(1100)).isFalse();
        assertVisible(muteButton, "Expected mute button to be still visible after 1s");
    }

    private void assertVisible(UiObject button, String message) {
        assertWithMessage(message).that(button.exists()).isTrue();
    }

    private void assertHidden(UiObject button, String message) {
        assertWithMessage(message).that(button.exists()).isFalse();
    }

    private static UiObject findViewSelectedButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_view_selected"));
    }

    private static UiObject findPreviewSelectedCheckButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_selected_check_button"));
    }


    private static UiObject findPlayerView() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_player_view"));
    }

    private static UiObject findMuteButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_mute"));
    }

    private static UiObject findPlayPauseButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/exo_play_pause"));
    }

    private static UiObject findPauseButton() {
        return new UiObject(new UiSelector().descriptionContains("Pause"));
    }

    private static UiObject findPlayButton() {
        return new UiObject(new UiSelector().descriptionContains("Play"));
    }

    private static UiObject findPreviewVideoImageView() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_video_image"));
    }

    private void swipeLeftAndWait() {
        final int width = mDevice.getDisplayWidth();
        final int height = mDevice.getDisplayHeight();
        mDevice.swipe(15 * width / 20, height / 2, width / 20, height / 2, 10);
        mDevice.waitForIdle();
    }

    private static List<String> getTestParameters() {
        return Arrays.asList(
                MediaStore.ACTION_PICK_IMAGES,
                Intent.ACTION_GET_CONTENT
        );
    }

    private void addMultipleSelectionFlag(Intent intent) {
        switch (intent.getAction()) {
            case MediaStore.ACTION_PICK_IMAGES:
                intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,
                        MediaStore.getPickImagesMaxLimit());
                break;
            case Intent.ACTION_GET_CONTENT:
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                break;
            default:
                // do nothing
        }
    }

    private void launchPhotoPickerForIntent(Intent intent) throws Exception {
        // GET_CONTENT needs to have setType
        if (Intent.ACTION_GET_CONTENT.equals(intent.getAction()) && intent.getType() == null) {
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        }

        mActivity.startActivityForResult(intent, REQUEST_CODE);
    }
}
