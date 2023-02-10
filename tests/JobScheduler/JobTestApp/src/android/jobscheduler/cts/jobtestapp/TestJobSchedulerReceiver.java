/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.jobscheduler.cts.jobtestapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Schedules jobs for this package but does not, by itself, occupy a foreground uid state
 * while doing so.
 */
public class TestJobSchedulerReceiver extends BroadcastReceiver {
    private static final String TAG = TestJobSchedulerReceiver.class.getSimpleName();
    private static final String PACKAGE_NAME = "android.jobscheduler.cts.jobtestapp";
    private static final String NOTIFICATION_CHANNEL_ID = TAG + "_channel";

    public static final String ACTION_JOB_SCHEDULE_RESULT =
            PACKAGE_NAME + ".action.SCHEDULE_RESULT";
    public static final String EXTRA_SCHEDULE_RESULT = PACKAGE_NAME + ".extra.SCHEDULE_RESULT";

    public static final String EXTRA_JOB_ID_KEY = PACKAGE_NAME + ".extra.JOB_ID";
    public static final String EXTRA_ALLOW_IN_IDLE = PACKAGE_NAME + ".extra.ALLOW_IN_IDLE";
    public static final String EXTRA_REQUIRED_NETWORK_TYPE =
            PACKAGE_NAME + ".extra.REQUIRED_NETWORK_TYPE";
    public static final String EXTRA_AS_EXPEDITED = PACKAGE_NAME + ".extra.AS_EXPEDITED";
    public static final String EXTRA_AS_USER_INITIATED = PACKAGE_NAME + ".extra.AS_USER_INITIATED";
    public static final String EXTRA_REQUEST_JOB_UID_STATE =
            PACKAGE_NAME + ".extra.REQUEST_JOB_UID_STATE";
    public static final String ACTION_SCHEDULE_JOB = PACKAGE_NAME + ".action.SCHEDULE_JOB";
    public static final String ACTION_CANCEL_JOBS = PACKAGE_NAME + ".action.CANCEL_JOBS";
    public static final String ACTION_POST_UI_INITIATING_NOTIFICATION =
            PACKAGE_NAME + ".action.POST_UI_INITIATING_NOTIFICATION";
    public static final String ACTION_NOTIFICATION_POSTED =
            PACKAGE_NAME + ".action.NOTIFICATION_POSTED";
    public static final int JOB_INITIAL_BACKOFF = 10_000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Processing intent: " + intent.getAction());
        final ComponentName jobServiceComponent = new ComponentName(context, TestJobService.class);
        final JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        switch (intent.getAction()) {
            case ACTION_CANCEL_JOBS:
                jobScheduler.cancelAll();
                Log.d(TAG, "Cancelled all jobs for " + context.getPackageName());
                break;
            case ACTION_SCHEDULE_JOB:
                final int jobId = intent.getIntExtra(EXTRA_JOB_ID_KEY, hashCode());
                final boolean allowInIdle = intent.getBooleanExtra(EXTRA_ALLOW_IN_IDLE, false);
                final int networkType =
                        intent.getIntExtra(EXTRA_REQUIRED_NETWORK_TYPE, JobInfo.NETWORK_TYPE_NONE);
                final boolean expedited = intent.getBooleanExtra(EXTRA_AS_EXPEDITED, false);
                final boolean userInitiated =
                        intent.getBooleanExtra(EXTRA_AS_USER_INITIATED, false);
                final boolean requestJobUidState = intent.getBooleanExtra(
                        EXTRA_REQUEST_JOB_UID_STATE, false);
                final Bundle extras = new Bundle();
                extras.putBoolean(EXTRA_REQUEST_JOB_UID_STATE, requestJobUidState);
                JobInfo.Builder jobBuilder = new JobInfo.Builder(jobId, jobServiceComponent)
                        .setBackoffCriteria(JOB_INITIAL_BACKOFF, JobInfo.BACKOFF_POLICY_LINEAR)
                        .setTransientExtras(extras)
                        .setImportantWhileForeground(allowInIdle)
                        .setExpedited(expedited)
                        .setUserInitiated(userInitiated)
                        .setRequiredNetworkType(networkType);
                final int result = jobScheduler.schedule(jobBuilder.build());
                if (result != JobScheduler.RESULT_SUCCESS) {
                    Log.e(TAG, "Could not schedule job " + jobId);
                } else {
                    Log.d(TAG, "Successfully scheduled job with id " + jobId);
                }

                final Intent scheduleJobResultIntent = new Intent(ACTION_JOB_SCHEDULE_RESULT);
                scheduleJobResultIntent.putExtra(EXTRA_JOB_ID_KEY, jobId);
                scheduleJobResultIntent.putExtra(EXTRA_SCHEDULE_RESULT, result);
                context.sendBroadcast(scheduleJobResultIntent);
                break;
            case ACTION_POST_UI_INITIATING_NOTIFICATION:
                NotificationManager notificationManager =
                        context.getSystemService(NotificationManager.class);
                NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                        TestJobSchedulerReceiver.class.getSimpleName(),
                        NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);

                final Intent scheduleJobIntent = new Intent(intent);
                scheduleJobIntent.setAction(ACTION_SCHEDULE_JOB);

                final PendingIntent scheduleJobPendingIntent = PendingIntent.getBroadcast(
                        context, 1, scheduleJobIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                Notification notification =
                        new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                                .setContentTitle("Test title")
                                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                                .setContentText("Click to start UI job!")
                                .setContentIntent(scheduleJobPendingIntent)
                                .build();
                notificationManager.notify(0, notification);

                final Intent completionIntent = new Intent(ACTION_NOTIFICATION_POSTED);
                context.sendBroadcast(completionIntent);
                break;
            default:
                Log.e(TAG, "Unknown action " + intent.getAction());
        }
    }
}