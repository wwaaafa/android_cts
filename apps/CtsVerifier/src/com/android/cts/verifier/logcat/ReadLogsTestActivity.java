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

package com.android.cts.verifier.logcat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A read_logs CTS Verifier test case for testing user consent for log data access.
 *
 * This test generates a per-use prompt when the requester is foreground.
 * If the requester is background, the log data access is denied.
 */
public class ReadLogsTestActivity extends PassFailButtons.Activity {

    /**
     * The name of the APP to test
     */
    private static final String TAG = "ReadLogsTestActivity";

    private static final String PERMISSION = "android.permission.READ_LOGS";

    private static final String ALLOW_LOGD_ACCESS = "Allow logd access";
    private static final String DENY_LOGD_ACCESS = "Decline logd access";
    private static final String SYSTEM_LOG_START = "--------- beginning of system";

    private static final int NUM_OF_LINES_FG = 10;
    private static final int NUM_OF_LINES_BG = 0;

    private static Context sContext;
    private static ActivityManager sActivityManager;

    private static String sAppPackageName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sContext = this;
        sActivityManager = sContext.getSystemService(ActivityManager.class);

        // Setup the UI.
        setContentView(R.layout.logcat_read_logs);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.read_logs_text, R.string.read_logs_test_info, -1);

        createView();
    }

    private void createView() {

        // Get the run test button and attach the listener.
        Button runFgAllowBtn = (Button) findViewById(R.id.run_read_logs_fg_allow_btn);
        runFgAllowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runLogcatInForegroundAllowOnlyOnce();
            }
        });

        Button runFgDenyBtn = (Button) findViewById(R.id.run_read_logs_fg_deny_btn);
        runFgDenyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runLogcatInForegroundDontAllow();
            }
        });

    }

    /**
     * Responsible for running the logcat in foreground and testing the allow button
     */
    public void runLogcatInForegroundAllowOnlyOnce() {
        Log.d(TAG, "Inside runLogcatInForeground()");
        BufferedReader reader = null;
        try {
            // Dump the logcat most recent 10 lines before the compile command,
            // and check if there are logs about compiling the test package.
            java.lang.Process logcat = new ProcessBuilder(
                    Arrays.asList("logcat", "-b", "system", "-t",
                        Integer.toString(NUM_OF_LINES_FG))).start();
            reader = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
            logcat.waitFor();

            List<String> logcatOutput = new ArrayList<>();
            String current;
            Integer lineCount = 0;
            while ((current = reader.readLine()) != null) {
                logcatOutput.add(current);
                lineCount++;
            }

            Log.d(TAG, "Logcat system allow line count: " + lineCount);
            Log.d(TAG, "Logcat system allow output: " + logcatOutput);

            try {

                assertTrue("System log output is null", logcatOutput.size() != 0);

                // Check if the logcatOutput is not null. If logcatOutput is null,
                // it throws an assertion error
                assertNotNull(logcatOutput.get(0), "logcat output should not be null");

                boolean allowLog = logcatOutput.get(0).contains(SYSTEM_LOG_START);
                assertTrue("Allow system log access containe log", allowLog);

                boolean allowLineCount = lineCount > NUM_OF_LINES_FG;
                assertTrue("Allow system log access count", allowLineCount);

                Log.d(TAG, "Logcat system allow log contains: " + allowLog + " lineCount: "
                        + lineCount + " larger than: " + allowLineCount);

            } catch (AssertionError e) {
                fail("User Consent Allow Testing failed");
            }

        } catch (Exception e) {
            Log.e(TAG, "User Consent Testing failed");
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                Log.d(TAG, "Could not close reader: " + e.getMessage());
            }
        }
    }

    private void fail(CharSequence reason) {
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        Log.e(TAG, reason.toString());
        setTestResultAndFinish(false);
    }

    /**
     * Responsible for running the logcat in foreground and testing the deny button
     */
    public void runLogcatInForegroundDontAllow() {
        Log.d(TAG, "Inside runLogcatInForeground()");
        BufferedReader reader = null;
        try {
            java.lang.Process logcat = new ProcessBuilder(
                    Arrays.asList("logcat", "-b", "system", "-t",
                        Integer.toString(NUM_OF_LINES_FG))).start();
            logcat.waitFor();

            // Merge several logcat streams, and take the last N lines
            reader = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
            assertNotNull(reader);

            List<String> logcatOutput = new ArrayList<>();
            String current;
            int lineCount = 0;
            while ((current = reader.readLine()) != null) {
                logcatOutput.add(current);
                lineCount++;
            }

            Log.d(TAG, "Logcat system deny line count:" + lineCount);

            try {
                assertTrue("Deny System log access", lineCount == NUM_OF_LINES_BG);
            }  catch (AssertionError e) {
                fail("User Consent Deny Testing failed");
            }

        } catch (Exception e) {
            Log.e(TAG, "User Consent Testing failed");
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                Log.d(TAG, "Could not close reader: " + e.getMessage());
            }
        }
    }

}
