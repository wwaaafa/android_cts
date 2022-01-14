/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.text.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.text.LineBreakConfig;
import android.os.LocaleList;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StaticLayoutLineBreakingVariantsTest {

    private static TextPaint setupPaint(LocaleList locales) {
        // The test font covers all KATAKANA LETTERS (U+30A1..U+30FC) in Japanese and they all have
        // 1em width.
        Context context = InstrumentationRegistry.getTargetContext();
        TextPaint paint = new TextPaint();
        paint.setTypeface(Typeface.createFromAsset(context.getAssets(),
                  "fonts/BreakVariantsTest.ttf"));
        paint.setTextSize(10.0f);  // Make 1em == 10px.
        paint.setTextLocales(locales);
        return paint;
    }

    private static StaticLayout buildLayout(String text, LocaleList locales,
            LineBreakConfig lineBreakConfig, int width) {
        StaticLayout.Builder builder = StaticLayout.Builder.obtain(text, 0, text.length(),
                setupPaint(locales), width);
        builder.setLineBreakConfig(lineBreakConfig);
        return builder.build();
    }

    private static void assertLineBreak(String text, String locale,
            LineBreakConfig lineBreakConfig, int width, String... expected) {
        final StaticLayout layout = buildLayout(text, LocaleList.forLanguageTags(locale),
                lineBreakConfig, width);
        assertEquals(expected.length, layout.getLineCount());

        int currentExpectedOffset = 0;
        for (int i = 0; i < expected.length; ++i) {
            currentExpectedOffset += expected[i].length();
            final String msg = i + "th line should be " + expected[i] + ", but it was "
                    + text.substring(layout.getLineStart(i), layout.getLineEnd(i));
            assertEquals(msg, currentExpectedOffset, layout.getLineEnd(i));
        }
    }

    // The following three test cases verifies the loose/normal/strict line break segmentations
    // works on Android. For more details see
    // http://cldr.unicode.org/development/development-process/design-proposals/specifying-text-break-variants-in-locale-ids

    // The test string is "Battery Saver" in Japanese. Here are the list of breaking points.
    //        \u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC\u30D0\u30FC
    // loose :^     ^     ^     ^     ^     ^     ^     ^     ^     ^
    // strict:^           ^     ^           ^           ^           ^
    private static final String SAMPLE_TEXT =
            "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC\u30D0\u30FC";

    @Test
    public void testBreakVariant_loose() {
        LineBreakConfig config = new LineBreakConfig();
        config.setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_LOOSE);
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 90, SAMPLE_TEXT);
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 80,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 70,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 60,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB",
                "\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 50,
                "\u30D0\u30C3\u30C6\u30EA\u30FC",
                "\u30BB\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 40,
                "\u30D0\u30C3\u30C6\u30EA",
                "\u30FC\u30BB\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 30,
                "\u30D0\u30C3\u30C6",
                "\u30EA\u30FC\u30BB",
                "\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 20,
                "\u30D0\u30C3",
                "\u30C6\u30EA",
                "\u30FC\u30BB",
                "\u30FC\u30D0",
                "\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 10,
                "\u30D0",
                "\u30C3",
                "\u30C6",
                "\u30EA",
                "\u30FC",
                "\u30BB",
                "\u30FC",
                "\u30D0",
                "\u30FC");
    }

    @Test
    public void testBreakVariant_strict() {
        LineBreakConfig config = new LineBreakConfig();
        config.setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_STRICT);
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 90, SAMPLE_TEXT);
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 80,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 70,
                "\u30D0\u30C3\u30C6\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 60,
                "\u30D0\u30C3\u30C6\u30EA\u30FC",
                "\u30BB\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 50,
                "\u30D0\u30C3\u30C6\u30EA\u30FC",
                "\u30BB\u30FC\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 40,
                "\u30D0\u30C3\u30C6",
                "\u30EA\u30FC\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 30,
                "\u30D0\u30C3\u30C6",
                "\u30EA\u30FC",
                "\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 20,
                "\u30D0\u30C3",
                "\u30C6",
                "\u30EA\u30FC",
                "\u30BB\u30FC",
                "\u30D0\u30FC");
        assertLineBreak(SAMPLE_TEXT, "ja-JP", config, 10,
                "\u30D0",
                "\u30C3",
                "\u30C6",
                "\u30EA",
                "\u30FC",
                "\u30BB",
                "\u30FC",
                "\u30D0",
                "\u30FC");
    }
}
