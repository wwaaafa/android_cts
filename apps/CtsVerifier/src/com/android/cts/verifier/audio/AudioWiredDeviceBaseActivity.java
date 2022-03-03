/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import android.content.Context;

import android.os.Bundle;
import android.os.Handler;

import android.util.Log;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;

abstract class AudioWiredDeviceBaseActivity extends PassFailButtons.Activity {
    private static final String TAG = AudioWiredDeviceBaseActivity.class.getSimpleName();

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    private Button mSupportsBtn;
    private Button mDoesntSupportBtn;

    protected boolean mSupportsWiredPeripheral;

    abstract protected void enableTestButtons(boolean enabled);
    abstract protected void calculatePass();

    // ReportLog schema
    private static final String KEY_WIRED_PORT_SUPPORTED = "wired_port_supported";

    private void recordWiredPortFound() {
        getReportLog().addValue(
                KEY_WIRED_PORT_SUPPORTED,
                mSupportsWiredPeripheral ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    protected void setup() {
        // The "Honor" system buttons
        (mSupportsBtn = (Button)findViewById(R.id.audio_wired_no)).setOnClickListener(mBtnClickListener);
        (mDoesntSupportBtn = (Button)findViewById(R.id.audio_wired_yes)).setOnClickListener(mBtnClickListener);

        enableTestButtons(false);
    }

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.audio_wired_no:
                    mSupportsWiredPeripheral = false;
                    mDoesntSupportBtn.setEnabled(false);
                    break;

                case R.id.audio_wired_yes:
                    Log.i(TAG, "User confirms wired device existence");
                    mSupportsWiredPeripheral = true;
                    mSupportsBtn.setEnabled(false);
                    break;
            }
            Log.i(TAG, "Wired Device Support:" + mSupportsWiredPeripheral);
            enableTestButtons(mSupportsWiredPeripheral);
            recordWiredPortFound();
            calculatePass();
        }
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public void recordTestResults() {
        getReportLog().submit();
    }
}
