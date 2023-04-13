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
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.jobscheduler.cts.jobtestapp.TestActivity;
import android.jobscheduler.cts.jobtestapp.TestFgsService;
import android.jobscheduler.cts.jobtestapp.TestJobSchedulerReceiver;
import android.os.SystemClock;
import android.os.UserHandle;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;
import android.util.SparseArray;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.AppStandbyUtils;
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
    private static final String TEST_APP_FGS = TEST_APP_PACKAGE + ".TestFgsService";
    static final String TEST_APP_RECEIVER = TEST_APP_PACKAGE + ".TestJobSchedulerReceiver";

    private final Context mContext;
    private final int mJobId;

    /* accesses must be synchronized on itself */
    private final SparseArray<TestJobState> mTestJobStates = new SparseArray();

    TestAppInterface(Context ctx, int jobId) {
        mContext = ctx;
        mJobId = jobId;

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_JOB_STARTED);
        intentFilter.addAction(ACTION_JOB_STOPPED);
        intentFilter.addAction(ACTION_JOB_SCHEDULE_RESULT);
        mContext.registerReceiver(mReceiver, intentFilter, Context.RECEIVER_EXPORTED_UNAUDITED);
        if (AppStandbyUtils.isAppStandbyEnabled()) {
            // Disable the bucket elevation so that we put the app in lower buckets.
            SystemUtil.runShellCommand(
                    "am compat enable --no-kill SCHEDULE_EXACT_ALARM_DOES_NOT_ELEVATE_BUCKET "
                            + TEST_APP_PACKAGE);
            // Force the test app out of the never bucket.
            SystemUtil.runShellCommand("am set-standby-bucket " + TEST_APP_PACKAGE + " rare");
        }
    }

    void cleanup() throws Exception {
        final Intent cancelJobsIntent = new Intent(TestJobSchedulerReceiver.ACTION_CANCEL_JOBS);
        cancelJobsIntent.setComponent(new ComponentName(TEST_APP_PACKAGE, TEST_APP_RECEIVER));
        cancelJobsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.sendBroadcast(cancelJobsIntent);
        closeActivity();
        stopFgs();
        mContext.unregisterReceiver(mReceiver);
        AppOpsUtils.reset(TEST_APP_PACKAGE);
        SystemUtil.runShellCommand("am compat --reset-all" + TEST_APP_PACKAGE);
        mTestJobStates.clear();
        forceStopApp(); // Clean up as much internal/temporary system state as possible
    }

    @Override
    public void close() throws Exception {
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

    void postFgsStartingAlarm() throws Exception {
        AppOpsUtils.setOpMode(TEST_APP_PACKAGE,
                AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM, AppOpsManager.MODE_ALLOWED);
        final Intent intent = new Intent(TestJobSchedulerReceiver.ACTION_SCHEDULE_FGS_START_ALARM);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.setComponent(new ComponentName(TEST_APP_PACKAGE, TEST_APP_RECEIVER));

        final CallbackAsserter resultBroadcastAsserter = CallbackAsserter.forBroadcast(
                new IntentFilter(TestJobSchedulerReceiver.ACTION_ALARM_SCHEDULED));
        mContext.sendBroadcast(intent);
        resultBroadcastAsserter.assertCalled("Didn't get alarm scheduled broadcast",
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

    void stopJob(int stopReason, int internalStopReason) throws Exception {
        SystemUtil.runShellCommand("cmd jobscheduler stop"
                + " -u " + UserHandle.myUserId()
                + " -s " + stopReason + " -i " + internalStopReason
                + " " + TEST_APP_PACKAGE + " " + mJobId);
    }

    void forceStopApp() {
        SystemUtil.runShellCommand("am force-stop"
                + " --user " + UserHandle.myUserId() + " " + TEST_APP_PACKAGE);
    }

    void cancelJob() throws Exception {
        SystemUtil.runShellCommand("cmd jobscheduler cancel"
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

    void startFgs() throws Exception {
        final Intent intent = new Intent(TestJobSchedulerReceiver.ACTION_START_FGS);
        intent.setComponent(new ComponentName(TEST_APP_PACKAGE, TEST_APP_RECEIVER));

        final CallbackAsserter resultBroadcastAsserter =
                CallbackAsserter.forBroadcast(new IntentFilter(TestFgsService.ACTION_FGS_STARTED));
        mContext.sendBroadcast(intent);
        resultBroadcastAsserter.assertCalled("Didn't get FGS started broadcast",
                15 /* 15 seconds */);
    }

    void stopFgs() {
        final Intent testFgs = new Intent(TestFgsService.ACTION_STOP_FOREGROUND);
        mContext.sendBroadcast(testFgs);
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
                    synchronized (mTestJobStates) {
                        TestJobState jobState = mTestJobStates.get(params.getJobId());
                        if (jobState == null) {
                            jobState = new TestJobState();
                            mTestJobStates.put(params.getJobId(), jobState);
                        } else {
                            jobState.reset();
                        }
                        jobState.running = ACTION_JOB_STARTED.equals(intent.getAction());
                        jobState.params = params;
                        // With these broadcasts, the job is/was running, and therefore scheduling
                        // was successful.
                        jobState.scheduleResult = JobScheduler.RESULT_SUCCESS;
                        if (intent.getBooleanExtra(EXTRA_REQUEST_JOB_UID_STATE, false)) {
                            jobState.procState = intent.getIntExtra(JOB_PROC_STATE_KEY,
                                    ActivityManager.PROCESS_STATE_NONEXISTENT);
                            jobState.capabilities = intent.getIntExtra(JOB_CAPABILITIES_KEY,
                                    ActivityManager.PROCESS_CAPABILITY_NONE);
                            jobState.oomScoreAdj = intent.getIntExtra(JOB_OOM_SCORE_ADJ_KEY,
                                    INVALID_ADJ);
                        }
                    }
                    break;
                case ACTION_JOB_SCHEDULE_RESULT:
                    synchronized (mTestJobStates) {
                        final int jobId = intent.getIntExtra(
                                TestJobSchedulerReceiver.EXTRA_JOB_ID_KEY, 0);
                        TestJobState jobState = mTestJobStates.get(jobId);
                        if (jobState == null) {
                            jobState = new TestJobState();
                            mTestJobStates.put(jobId, jobState);
                        } else {
                            jobState.reset();
                        }
                        jobState.running = false;
                        jobState.params = null;
                        jobState.scheduleResult = intent.getIntExtra(
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
            synchronized (mTestJobStates) {
                TestJobState jobState = mTestJobStates.get(jobId);
                return jobState != null && jobState.running;
            }
        });
    }

    boolean awaitJobStop(long maxWait) throws Exception {
        return waitUntilTrue(maxWait, () -> {
            synchronized (mTestJobStates) {
                TestJobState jobState = mTestJobStates.get(mJobId);
                return jobState != null && !jobState.running;
            }
        });
    }

    void assertJobUidState(int procState, int capabilities, int oomScoreAdj) {
        synchronized (mTestJobStates) {
            TestJobState jobState = mTestJobStates.get(mJobId);
            if (jobState == null) {
                fail("Job not started");
            }
            assertEquals("procState expected=" + procStateToString(procState)
                            + ",actual=" + procStateToString(jobState.procState),
                    procState, jobState.procState);
            assertEquals("capabilities expected=" + getCapabilitiesSummary(capabilities)
                            + ",actual=" + getCapabilitiesSummary(jobState.capabilities),
                    capabilities, jobState.capabilities);
            assertEquals("Unexpected oomScoreAdj", oomScoreAdj, jobState.oomScoreAdj);
        }
    }

    boolean awaitJobScheduleResult(long maxWaitMs, int jobResult) throws Exception {
        return awaitJobScheduleResult(mJobId, maxWaitMs, jobResult);
    }

    boolean awaitJobScheduleResult(int jobId, long maxWaitMs, int jobResult) throws Exception {
        return waitUntilTrue(maxWaitMs, () -> {
            synchronized (mTestJobStates) {
                TestJobState jobState = mTestJobStates.get(jobId);
                return jobState != null && jobState.scheduleResult == jobResult;
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
        synchronized (mTestJobStates) {
            TestJobState jobState = mTestJobStates.get(mJobId);
            return jobState == null ? null : jobState.params;
        }
    }

    private static final class TestJobState {
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
