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

import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;

/**
 * A class to help test case to start background activity.
 */
public class VirtualDisplayReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean usePublicPresentation = intent.getBooleanExtra(
                Components.VirtualDisplayReceiver.USE_PUBLIC_PRESENTATION, false);
        if (usePublicPresentation) {
            createPublicVirtualDisplayAndShowPresentation(context);
        } else {
            createVirtualDisplayAndShowPresentation(context);
        }
    }

    private void createPublicVirtualDisplayAndShowPresentation(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        VirtualDisplay virtualDisplay = displayManager.createVirtualDisplay(
                "VirtualDisplay1", 10, 10, 10, null,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        + DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                        + DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        new Presentation(context, virtualDisplay.getDisplay()).show();
    }

    private void createVirtualDisplayAndShowPresentation(Context context) {
        VirtualDisplay virtualDisplay = context.getSystemService(
                DisplayManager.class).createVirtualDisplay(
                "VirtualDisplay1", 10, 10, 10, null, 0);
        new Presentation(context, virtualDisplay.getDisplay()).show();
    }
}
