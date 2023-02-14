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
package android.jobscheduler.cts;

import static android.app.ActivityManager.getCapabilitiesSummary;
import static android.app.ActivityManager.procStateToString;
import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.ACTION_JOB_SCHEDULE_RESULT;
import static android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver.EXTRA_REQUEST_JOB_UID_STATE;
import static android.jobscheduler.cts.jobtestapp.TestJobService.ACTION_JOB_STARTED;
import static android.jobscheduler.cts.jobtestapp.TestJobService.ACTION_JOB_STOPPED;
import static android.jobscheduler.cts.jobtestapp.TestJobService.INVALID_ADJ;
import static android.jobscheduler.cts.jobtestapp.TestJobService.JOB_CAPABILITIES_KEY;
import static android.jobscheduler.cts.jobtestapp.TestJobService.JOB_OOM_SCORE_ADJ_KEY;
import static android.jobscheduler.cts.jobtestapp.TestJobService.JOB_PARAMS_EXTRA_KEY;
import static android.jobscheduler.cts.jobtestapp.TestJobService.JOB_PROC_STATE_KEY;
import static android.server.wm.WindowManagerState.STATE_RESUMED;

import static org.junit.Assert.assertEquals;

import android.app.ActivityManager;
import android.app.job.JobParameters;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.jobscheduler.cts.jobtestapp.TestActivity;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.os.SystemClock;
import android.os.UserHandle;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;

import com.android.compatibility.common.util.CallbackAsserter;
import com.android.compatibility.common.util.SystemUtil;

import java.util.Map;

/**
 * Common functions to interact with the test app.
 */
class TestAppInterface implements AutoCloseable {
    private static final String TAG = TestAppInterface.class.getSimpleName();

    static final String TEST_APP_PACKAGE = "android.jobscheduler.cts.jobtestapp";
    private static final String TEST_APP_ACTIVITY = TEST_APP_PACKAGE + ".TestActivity";
    static final String TEST_APP_RECEIVER = TEST_APP_PACKAGE + ".TestJobSchedulerReceiver";

    private final Context mContext;
    private final int mJobId;

    /* accesses must be synchronized on itself */
    private final TestJobState mTestJobState = new TestJobState();

    TestAppInterface(Context ctx, int jobId) {
        mContext = ctx;
        mJobId = jobId;

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_JOB_STARTED);
        intentFilter.addAction(ACTION_JOB_STOPPED);
        intentFilter.addAction(ACTION_JOB_SCHEDULE_RESULT);
        mContext.registerReceiver(mReceiver, intentFilter, Context.RECEIVER_EXPORTED_UNAUDITED);
    }

    void cleanup() {
        final Intent cancelJobsIntent = new Intent(TestJobSchedulerReceiver.ACTION_CANCEL_JOBS);
        cancelJobsIntent.setComponent(new ComponentName(TEST_APP_PACKAGE, TEST_APP_RECEIVER));
        cancelJobsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.sendBroadcast(cancelJobsIntent);
        closeActivity();
        mContext.unregisterReceiver(mReceiver);
        mTestJobState.reset();
    }

    @Override
    public void close() {
        cleanup();
    }

    void scheduleJob(boolean allowWhileIdle, int requiredNetworkType, boolean asExpeditedJob)
            throws Exception {
        scheduleJob(allowWhileIdle, requiredNetworkType, asExpeditedJob, false);
    }

    void scheduleJob(boolean allowWhileIdle, int requiredNetworkType, boolean asExpeditedJob,
            boolean asUserInitiatedJob) throws Exception {
        scheduleJob(
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_ALLOW_IN_IDLE, allowWhileIdle,
                        TestJobSchedulerReceiver.EXTRA_AS_EXPEDITED, asExpeditedJob,
                        TestJobSchedulerReceiver.EXTRA_AS_USER_INITIATED, asUserInitiatedJob
                ),
                Map.of(
                        TestJobSchedulerReceiver.EXTRA_REQUIRED_NETWORK_TYPE, requiredNetworkType
                ));
    }

    private Intent generateScheduleJobIntent(Map<String, Boolean> booleanExtras,
            Map<String, Integer> intExtras) {
        final Intent scheduleJobIntent = new Intent(TestJobSchedulerReceiver.ACTION_SCHEDULE_JOB);
        scheduleJobIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        if (!intExtras.containsKey(TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY)) {
            scheduleJobIntent.putExtra(TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, mJobId);
        }
        booleanExtras.forEach(scheduleJobIntent::putExtra);
        intExtras.forEach(scheduleJobIntent::putExtra);
        scheduleJobIntent.setComponent(new ComponentName(TEST_APP_PACKAGE, TEST_APP_RECEIVER));
        return scheduleJobIntent;
    }

    void scheduleJob(Map<String, Boolean> booleanExtras, Map<String, Integer> intExtras)
            throws Exception {
        final Intent scheduleJobIntent = generateScheduleJobIntent(booleanExtras, intExtras);

        final CallbackAsserter resultBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(TestJobSchedulerReceiver.ACTION_JOB_SCHEDULE_RESULT));
        mContext.sendBroadcast(scheduleJobIntent);
        resultBroadcastAsserter.assertCalled("Didn't get schedule job result broadcast",
                15 /* 15 seconds */);
    }

    void postUiInitiatingNotification(Map<String, Boolean> booleanExtras,
            Map<String, Integer> intExtras) throws Exception {
        final Intent intent = generateScheduleJobIntent(booleanExtras, intExtras);
        intent.setAction(TestJobSchedulerReceiver.ACTION_POST_UI_INITIATING_NOTIFICATION);

        final CallbackAsserter resultBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(TestJobSchedulerReceiver.ACTION_NOTIFICATION_POSTED));
        mContext.sendBroadcast(intent);
        resultBroadcastAsserter.assertCalled("Didn't get notification posted broadcast",
                15 /* 15 seconds */);
    }

    /** Asks (not forces) JobScheduler to run the job if constraints are met. */
    void runSatisfiedJob() throws Exception {
        SystemUtil.runShellCommand("cmd jobscheduler run -s"
                + " -u " + UserHandle.myUserId() + " " + TEST_APP_PACKAGE + " " + mJobId);
    }

    /** Forces JobScheduler to run the job */
    void forceRunJob() throws Exception {
        SystemUtil.runShellCommand("cmd jobscheduler run -f"
                + " -u " + UserHandle.myUserId() + " " + TEST_APP_PACKAGE + " " + mJobId);
    }

    void startAndKeepTestActivity() {
        startAndKeepTestActivity(false);
    }

    void startAndKeepTestActivity(boolean waitForResume) {
        final Intent testActivity = new Intent();
        testActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ComponentName testComponentName = new ComponentName(TEST_APP_PACKAGE, TEST_APP_ACTIVITY);
        testActivity.setComponent(testComponentName);
        mContext.startActivity(testActivity);
        if (waitForResume) {
            new WindowManagerStateHelper().waitForActivityState(testComponentName, STATE_RESUMED);
        }
    }

    void closeActivity() {
        closeActivity(false);
    }

    void closeActivity(boolean waitForClose) {
        mContext.sendBroadcast(new Intent(TestActivity.ACTION_FINISH_ACTIVITY));
        if (waitForClose) {
            ComponentName testComponentName =
                    new ComponentName(TEST_APP_PACKAGE, TEST_APP_ACTIVITY);
            new WindowManagerStateHelper().waitForActivityRemoved(testComponentName);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received action " + intent.getAction());
            switch (intent.getAction()) {
                case ACTION_JOB_STARTED:
                case ACTION_JOB_STOPPED:
                    final JobParameters params = intent.getParcelableExtra(JOB_PARAMS_EXTRA_KEY);
                    Log.d(TAG, "JobId: " + params.getJobId());
                    synchronized (mTestJobState) {
                        mTestJobState.running = ACTION_JOB_STARTED.equals(intent.getAction());
                        mTestJobState.jobId = params.getJobId();
                        mTestJobState.params = params;
                        if (intent.getBooleanExtra(EXTRA_REQUEST_JOB_UID_STATE, false)) {
                            mTestJobState.procState = intent.getIntExtra(JOB_PROC_STATE_KEY,
                                    ActivityManager.PROCESS_STATE_NONEXISTENT);
                            mTestJobState.capabilities = intent.getIntExtra(JOB_CAPABILITIES_KEY,
                                    ActivityManager.PROCESS_CAPABILITY_NONE);
                            mTestJobState.oomScoreAdj = intent.getIntExtra(JOB_OOM_SCORE_ADJ_KEY,
                                    INVALID_ADJ);
                        }
                    }
                    break;
                case ACTION_JOB_SCHEDULE_RESULT:
                    synchronized (mTestJobState) {
                        mTestJobState.running = false;
                        mTestJobState.jobId = intent.getIntExtra(
                                TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, 0);
                        mTestJobState.params = null;
                        mTestJobState.scheduleResult = intent.getIntExtra(
                                TestJobSchedulerReceiver.EXTRA_SCHEDULE_RESULT, -1);
                    }
                    break;
            }
        }
    };

    boolean awaitJobStart(long maxWait) throws Exception {
        return awaitJobStart(mJobId, maxWait);
    }

    boolean awaitJobStart(int jobId, long maxWait) throws Exception {
        return waitUntilTrue(maxWait, () -> {
            synchronized (mTestJobState) {
                return (mTestJobState.jobId == jobId) && mTestJobState.running;
            }
        });
    }

    boolean awaitJobStop(long maxWait) throws Exception {
        return waitUntilTrue(maxWait, () -> {
            synchronized (mTestJobState) {
                return (mTestJobState.jobId == mJobId) && !mTestJobState.running;
            }
        });
    }

    void assertJobUidState(int procState, int capabilities, int oomScoreAdj) {
        synchronized (mTestJobState) {
            assertEquals("procState expected=" + procStateToString(procState)
                    + ",actual=" + procStateToString(mTestJobState.procState),
                    procState, mTestJobState.procState);
            assertEquals("capabilities expected=" + getCapabilitiesSummary(capabilities)
                    + ",actual=" + getCapabilitiesSummary(mTestJobState.capabilities),
                    capabilities, mTestJobState.capabilities);
            assertEquals("Unexpected oomScoreAdj", oomScoreAdj, mTestJobState.oomScoreAdj);
        }
    }

    boolean awaitJobScheduleResult(long maxWaitMs, int jobResult) throws Exception {
        return waitUntilTrue(maxWaitMs, () -> {
            synchronized (mTestJobState) {
                return mTestJobState.jobId == mJobId && mTestJobState.scheduleResult == jobResult;
            }
        });
    }

    private boolean waitUntilTrue(long maxWait, Condition condition) throws Exception {
        final long deadLine = SystemClock.uptimeMillis() + maxWait;
        do {
            Thread.sleep(500);
        } while (!condition.isTrue() && SystemClock.uptimeMillis() < deadLine);
        return condition.isTrue();
    }

    JobParameters getLastParams() {
        synchronized (mTestJobState) {
            return mTestJobState.params;
        }
    }

    private static final class TestJobState {
        int jobId;
        int scheduleResult;
        boolean running;
        int procState;
        int capabilities;
        int oomScoreAdj;
        JobParameters params;

        TestJobState() {
            initState();
        }

        private void reset() {
            initState();
        }

        private void initState() {
            running = false;
            procState = ActivityManager.PROCESS_STATE_NONEXISTENT;
            capabilities = ActivityManager.PROCESS_CAPABILITY_NONE;
            oomScoreAdj = INVALID_ADJ;
            scheduleResult = -1;
        }
    }

    private interface Condition {
        boolean isTrue() throws Exception;
    }
}
