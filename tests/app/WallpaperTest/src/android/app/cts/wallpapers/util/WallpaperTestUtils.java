/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.app.cts.wallpapers.util;

import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;

import static com.google.common.truth.Truth.assertThat;

import android.app.WallpaperManager;
import android.app.cts.wallpapers.R;
import android.app.cts.wallpapers.TestLiveWallpaper;
import android.app.cts.wallpapers.TestLiveWallpaperNoUnfoldTransition;
import android.app.cts.wallpapers.TestLiveWallpaperSupportingAmbientMode;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class WallpaperTestUtils {

    private static final String TAG = "WallpaperTestUtils";

    /**
     * Helper get a bitmap from a drawable
     */
    public static Bitmap getBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());

        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return result;
    }

    /**
     * Helper to check whether two bitmaps are similar.
     * <br>
     * This comparison is not perfect at all; but good enough for the purpose of wallpaper tests.
     * <br>
     * This method is designed to be tolerant enough to avoid false negative (i.e. returning false
     * when two bitmaps of the same image with different configurations are compared),
     * but strict enough to return false when comparing two different bitmaps unless they are
     * really similar.
     * <br>
     * It will always return false for two bitmaps with different dimensions.
     */
    public static boolean isSimilar(Bitmap bitmap1, Bitmap bitmap2) {
        int width = bitmap1.getWidth();
        int height = bitmap1.getHeight();
        if (width != bitmap2.getWidth() || height != bitmap2.getHeight()) return false;

        // two pixels are considered similar if each of their ARGB value has at most a 10% diff.
        float tolerance = 0.1f;

        // two bitmaps are considered similar if at least 90% of pixels are similar
        float acceptedMismatchingPixelRate = 0.1f;

        // only test 1% of the pixels
        int totalPixelTested = width * height / 100;

        return IntStream.range(0, totalPixelTested).map(c -> 100 * c).filter(c -> {
            int x = c % width;
            int y = c / width;
            int pixel1 = bitmap1.getPixel(x, y);
            int pixel2 = bitmap2.getPixel(x, y);
            return Stream
                    .<IntFunction<Integer>>of(Color::alpha, Color::red, Color::green, Color::blue)
                    .mapToInt(channel -> Math.abs(channel.apply(pixel1) - channel.apply(pixel2)))
                    .anyMatch(delta -> delta / 256f > tolerance);
        }).count() / (float) (totalPixelTested) <= acceptedMismatchingPixelRate;
    }

    /**
     * Helper to check whether two drawables are similar.
     * <br>
     * Uses {@link #getBitmap} to convert the drawables to bitmap,
     * then {@link #isSimilar(Bitmap, Bitmap)} to perform the similarity comparison.
     */
    public static boolean isSimilar(Drawable drawable1, Drawable drawable2) {
        return isSimilar(getBitmap(drawable1), getBitmap(drawable2));
    }

    public static final ComponentName TEST_LIVE_WALLPAPER_COMPONENT = new ComponentName(
            TestLiveWallpaper.class.getPackageName(),
            TestLiveWallpaper.class.getName());

    public static final ComponentName TEST_LIVE_WALLPAPER_NO_UNFOLD_COMPONENT = new ComponentName(
            TestLiveWallpaperNoUnfoldTransition.class.getPackageName(),
            TestLiveWallpaperNoUnfoldTransition.class.getName());

    public static final ComponentName TEST_LIVE_WALLPAPER_AMBIENT_COMPONENT = new ComponentName(
            TestLiveWallpaperSupportingAmbientMode.class.getPackageName(),
            TestLiveWallpaperSupportingAmbientMode.class.getName());

    /**
     * enumeration of all wallpapers used for test purposes: 3 static, 3 live wallpapers:   <br>
     * static1 <=> red bitmap <br>
     * static2 <=> green bitmap <br>
     * static3 <=> blue bitmap <br>
     * <br>
     * live1 <=> TestLiveWallpaper (cyan) <br>
     * live2 <=> TestLiveWallpaperNoUnfoldTransition (magenta) <br>
     * live3 <=> TestLiveWallpaperSupportingAmbientMode (yellow) <br>
     */
    public enum TestWallpaper {
        STATIC1(R.drawable.icon_red, null),
        STATIC2(R.drawable.icon_green, null),
        STATIC3(R.drawable.icon_blue, null),
        LIVE1(null, TEST_LIVE_WALLPAPER_COMPONENT),
        LIVE2(null, TEST_LIVE_WALLPAPER_NO_UNFOLD_COMPONENT),
        LIVE3(null, TEST_LIVE_WALLPAPER_AMBIENT_COMPONENT);

        private final Integer mBitmapResourceId;
        private final ComponentName mComponentName;

        TestWallpaper(Integer bitmapResourceId, ComponentName componentName) {
            mBitmapResourceId = bitmapResourceId;
            mComponentName = componentName;
        }

        int getBitmapResourceId() {
            return mBitmapResourceId;
        }

        ComponentName getComponentName() {
            return mComponentName;
        }

        private String type() {
            return isStatic() ? "static" : "live";
        }

        private boolean isStatic() {
            return mComponentName == null;
        }

        private boolean isLive() {
            return !isStatic();
        }
    }

    private static List<TestWallpaper> allStaticTestWallpapers() {
        return List.of(TestWallpaper.STATIC1, TestWallpaper.STATIC2, TestWallpaper.STATIC3);
    }

    private static List<TestWallpaper> allLiveTestWallpapers() {
        return List.of(TestWallpaper.LIVE1, TestWallpaper.LIVE2, TestWallpaper.LIVE3);
    }

    public static class WallpaperChange {
        TestWallpaper mWallpaper;
        int mDestination;
        WallpaperChange(
                TestWallpaper wallpaper, int destination) {
            this.mWallpaper = wallpaper;
            this.mDestination = destination;
        }
    }

    /**
     * Class representing a state in which our WallpaperManager may be during our tests.
     * A state is fully represented by the wallpaper that are present on home and lock screen.
     */
    public static class State {
        private final TestWallpaper mHomeWallpaper;
        private final TestWallpaper mLockWallpaper;

        /**
         * it is possible to have two copies of the same engine on home + lock screen,
         * in which this flag would be false.
         * True means that mHomeWallpaper == mLockWallpaper and there is only one active engine.
         */
        private final boolean mSingleEngine;

        private State(
                TestWallpaper homeWallpaper, TestWallpaper lockWallpaper, boolean singleEngine) {
            mHomeWallpaper = homeWallpaper;
            mLockWallpaper = lockWallpaper;
            assertThat(!singleEngine || (homeWallpaper == lockWallpaper)).isTrue();
            mSingleEngine = singleEngine;
        }

        private TestWallpaper pickUnused(List<TestWallpaper> choices) {
            return choices.stream()
                    .filter(wallpaper -> wallpaper != mHomeWallpaper && wallpaper != mLockWallpaper)
                    .findFirst().get();
        }

        private TestWallpaper pickUnusedStatic() {
            return pickUnused(allStaticTestWallpapers());
        }

        private TestWallpaper pickUnusedLive() {
            return pickUnused(allLiveTestWallpapers());
        }

        /**
         * @return true if there is at least one live wallpaper in this state
         */
        public boolean hasAtLeastOneLiveWallpaper() {
            return mHomeWallpaper.isLive() || mLockWallpaper.isLive();
        }

        /**
         * Enumerate all the possible logically different {@link WallpaperChange} changes from
         * this state. <br>
         * Two changes are considered logically different if their destination is different,
         * or if their wallpaper type (static or live) is different.
         */
        public List<WallpaperChange> allPossibleChanges() {
            TestWallpaper unusedStatic = pickUnusedStatic();
            TestWallpaper unusedLive = pickUnusedLive();

            // one can always add a new wallpaper, either static or live, at any destination
            List<WallpaperChange> result = new ArrayList<>(List.of(unusedStatic, unusedLive)
                    .stream()
                    .flatMap(newWallpaper -> Stream
                            .of(FLAG_LOCK, FLAG_SYSTEM, FLAG_LOCK | FLAG_SYSTEM)
                            .map(destination -> new WallpaperChange(newWallpaper, destination)))
                    .toList());

            // if we have a lock & home single engine, we can separate it
            if (mSingleEngine) {
                result.addAll(List.of(
                        new WallpaperChange(mHomeWallpaper, FLAG_SYSTEM),
                        new WallpaperChange(mHomeWallpaper, FLAG_LOCK)
                ));

            // else if we have the same engine twice, we can merge it
            } else if (mHomeWallpaper == mLockWallpaper) {
                result.add(new WallpaperChange(mHomeWallpaper, FLAG_SYSTEM | FLAG_LOCK));
            }

            // if we have different engines on home / lock,
            // we can set one of them at the other location or at both locations
            if (mHomeWallpaper != mLockWallpaper) {
                result.addAll(List.of(
                        new WallpaperChange(mHomeWallpaper, FLAG_LOCK | FLAG_SYSTEM),
                        new WallpaperChange(mLockWallpaper, FLAG_LOCK | FLAG_SYSTEM),
                        new WallpaperChange(mHomeWallpaper, FLAG_LOCK),
                        new WallpaperChange(mLockWallpaper, FLAG_SYSTEM)
                ));
            }
            return result;
        }

        /**
         * Given a change, return the number of times we expect an engine.onCreate operation
         * of a live wallpaper from this state
         */
        public int expectedNumberOfLiveWallpaperCreate(WallpaperChange change) {

            if (change.mWallpaper.isStatic()) return 0;
            switch (change.mDestination) {
                case FLAG_SYSTEM | FLAG_LOCK:
                case FLAG_SYSTEM:
                    return change.mWallpaper != mHomeWallpaper ? 1 : 0;
                case FLAG_LOCK:
                    return change.mWallpaper != mLockWallpaper ? 1 : 0;
                default:
                    throw new IllegalArgumentException();
            }
        }


        /**
         * Given a change, return the number of times we expect a engine.onDestroy operation
         * of a live wallpaper from this state
         */
        public int expectedNumberOfLiveWallpaperDestroy(WallpaperChange change) {

            if (mSingleEngine) {
                return mHomeWallpaper.isLive()
                        && mHomeWallpaper != change.mWallpaper
                        && change.mDestination == (FLAG_LOCK | FLAG_SYSTEM) ? 1 : 0;
            }

            boolean systemReplaced = (change.mDestination & FLAG_SYSTEM) != 0
                    && change.mWallpaper != mHomeWallpaper;
            boolean lockReplaced = (change.mDestination & FLAG_LOCK) != 0
                    && change.mWallpaper != mLockWallpaper;

            int result = 0;
            if (systemReplaced && mHomeWallpaper.isLive()) result += 1;
            if (lockReplaced && mLockWallpaper.isLive()) result += 1;
            return result;
        }

        /**
         * Describes how to reproduce a failure obtained from this state with the given change
         */
        public String reproduceDescription(WallpaperChange change) {
            return String.format("To reproduce, start with:\n%s\nand %s",
                    description(), changeDescription(change));
        }

        private String description() {
            String homeType = mHomeWallpaper.type();
            String lockType = mLockWallpaper.type();
            return mLockWallpaper == mHomeWallpaper
                    ? String.format(" - the same %s wallpaper on home & lock screen (%s)", homeType,
                            mSingleEngine ? "sharing the same engine" : "each using its own engine")
                    : String.format(" - a %s wallpaper on home screen\n"
                            + " - %s %s wallpaper on lock screen",
                            homeType, homeType == lockType ? "another" : "a", lockType);
        }

        private String changeDescription(WallpaperChange change) {
            String newWallpaperDescription =
                    change.mWallpaper == mHomeWallpaper || change.mWallpaper == mLockWallpaper
                    ? String.format("the same %s wallpaper than %s screen",
                            change.mWallpaper.type(),
                            change.mWallpaper == mHomeWallpaper ? "home" : "lock")
                    : String.format("a different %s wallpaper", change.mWallpaper.type());

            String destinationDescription =
                    change.mDestination == FLAG_SYSTEM ? "home screen only"
                    : change.mDestination == FLAG_LOCK ? "lock screen only"
                    : "both home & lock screens";

            String methodUsed = change.mWallpaper.isLive()
                    ? "setWallpaperComponentWithFlags" : "setResource";

            String flagDescription =
                    change.mDestination == FLAG_SYSTEM ? "FLAG_SYSTEM"
                    : change.mDestination == FLAG_LOCK ? "FLAG_LOCK"
                    : "FLAG_SYSTEM|FLAG_LOCK";

            return String.format("apply %s on %s (via WallpaperManager#%s(..., %s))",
                    newWallpaperDescription, destinationDescription, methodUsed, flagDescription);
        }
    }

    /**
     * Uses the provided wallpaperManager instance to perform a {@link WallpaperChange}.
     */
    public static void performChange(
            WallpaperManager wallpaperManager, WallpaperChange change) throws IOException {
        if (change.mWallpaper.isStatic()) {
            wallpaperManager.setResource(
                    change.mWallpaper.getBitmapResourceId(), change.mDestination);
        } else {
            wallpaperManager.setWallpaperComponentWithFlags(
                    change.mWallpaper.getComponentName(), change.mDestination);
        }
        try {
            // Allow time for callbacks
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Log.e(TAG, "Live wallpaper wait interrupted", e);
        }
    }

    /**
     * Sets a wallpaperManager in some state. Always proceeds the same way: <br>
     *   - put the home wallpaper on lock and home screens <br>
     *   - put the lock wallpaper on lock screen, if it is different from the home screen wallpaper
     */
    public static void goToState(WallpaperManager wallpaperManager, State state)
            throws IOException {
        WallpaperChange change1 = new WallpaperChange(
                state.mHomeWallpaper, FLAG_SYSTEM | FLAG_LOCK);
        performChange(wallpaperManager, change1);

        WallpaperChange change2 = new WallpaperChange(state.mLockWallpaper, FLAG_LOCK);
        if (state.mLockWallpaper != state.mHomeWallpaper) performChange(wallpaperManager, change2);
    }

    /**
     * Return a list of all logically different states
     * Two states are logically different if at least one of this statement: <br>
     *   - home screen is live <br>
     *   - lock screen is live <br>
     *   - home screen and lock screen are the same wallpaper <br>
     *   - home screen and lock screen share the same engine <br>
     *  is different between the two states.
     */
    public static List<State> allPossibleStates() {
        return List.of(
                new State(TestWallpaper.LIVE1, TestWallpaper.LIVE1, true),
                new State(TestWallpaper.LIVE1, TestWallpaper.LIVE1, false),
                new State(TestWallpaper.LIVE1, TestWallpaper.LIVE2, false),
                new State(TestWallpaper.LIVE1, TestWallpaper.STATIC1, false),
                new State(TestWallpaper.STATIC1, TestWallpaper.STATIC1, true),
                new State(TestWallpaper.STATIC1, TestWallpaper.STATIC1, false),
                new State(TestWallpaper.STATIC1, TestWallpaper.STATIC2, false),
                new State(TestWallpaper.STATIC1, TestWallpaper.LIVE1, false)
        );
    }
}
