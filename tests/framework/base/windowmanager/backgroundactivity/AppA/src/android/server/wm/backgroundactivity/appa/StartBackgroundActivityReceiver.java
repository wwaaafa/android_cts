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
 * limitations under the License
 */

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.common.CommonComponents.EVENT_NOTIFIER_EXTRA;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.server.wm.backgroundactivity.common.CommonComponents.Event;

/**
 * A class to help test case to start background activity.
 */
public class StartBackgroundActivityReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Components appA = Components.get(context);

        ResultReceiver eventNotifier = intent.getParcelableExtra(EVENT_NOTIFIER_EXTRA);
        if (eventNotifier != null) {
            eventNotifier.send(Event.APP_A_START_BACKGROUND_ACTIVITY_BROADCAST_RECEIVED, null);
        }

        if (!intent.hasExtra(appA.START_ACTIVITY_RECEIVER_EXTRA.START_ACTIVITY_DELAY_MS)) {
            startActivityNow(context);
            return;
        }
        final int startActivityDelayMs = intent.getIntExtra(
                appA.START_ACTIVITY_RECEIVER_EXTRA.START_ACTIVITY_DELAY_MS, 0);
        new Thread(() -> {
            SystemClock.sleep(startActivityDelayMs);
            startActivityNow(context);
        }).start();
    }

    private void startActivityNow(Context context) {
        Intent newIntent = new Intent(context, BackgroundActivity.class);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(newIntent);
    }
}
