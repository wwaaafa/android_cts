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

package android.jobscheduler.cts;

import static com.android.compatibility.common.util.TestUtils.waitUntil;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.service.notification.StatusBarNotification;

/**
 * Tests related to attaching notifications to jobs via
 * {@link JobService#setNotification(JobParameters, int, Notification, int)}
 */
public class NotificationTest extends BaseJobSchedulerTest {
    private static final int JOB_ID = NotificationTest.class.hashCode();
    private static final String NOTIFICATION_CHANNEL_ID =
            NotificationTest.class.getSimpleName() + "_channel";

    private NotificationManager mNotificationManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mNotificationManager = getContext().getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NotificationTest.class.getSimpleName(), NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(channel);
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(JOB_ID);
        mNotificationManager.cancelAll();

        // The super method should be called at the end.
        super.tearDown();
    }

    public void testNotificationDetachOnJobStop() throws Exception {
        mNotificationManager.cancelAll();
        final int notificationId = 123;
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(notificationId, notification,
                JobService.JOB_END_NOTIFICATION_POLICY_DETACH);
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        waitUntil("Notification wasn't posted", 15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == notificationId;
                });

        kTestEnvironment.setExpectedStopped();
        mJobScheduler.cancel(JOB_ID);
        assertTrue(kTestEnvironment.awaitStopped());

        Thread.sleep(1000); // Wait a bit for NotificationManager to catch up
        // Notification should remain
        StatusBarNotification[] activeNotifications = mNotificationManager.getActiveNotifications();
        assertEquals(1, activeNotifications.length);
        assertEquals(notificationId, activeNotifications[0].getId());
    }

    public void testNotificationRemovedOnJobStop() throws Exception {
        mNotificationManager.cancelAll();
        final int notificationId = 123;
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, kJobServiceComponent).build();

        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
                .setContentTitle("test title")
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentText("test content")
                .build();

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setContinueAfterStart();
        kTestEnvironment.setNotificationAtStart(notificationId, notification,
                JobService.JOB_END_NOTIFICATION_POLICY_REMOVE);
        mJobScheduler.schedule(jobInfo);
        assertTrue("Job didn't start", kTestEnvironment.awaitExecution());

        waitUntil("Notification wasn't posted", 15 /* seconds */,
                () -> {
                    StatusBarNotification[] activeNotifications =
                            mNotificationManager.getActiveNotifications();
                    return activeNotifications.length == 1
                            && activeNotifications[0].getId() == notificationId;
                });

        kTestEnvironment.setExpectedStopped();
        mJobScheduler.cancel(JOB_ID);
        assertTrue(kTestEnvironment.awaitStopped());

        waitUntil("Notification wasn't removed", 15 /* seconds */,
                () -> {
                    // Notification should be gone
                    return mNotificationManager.getActiveNotifications().length == 0;
                });
    }
}
