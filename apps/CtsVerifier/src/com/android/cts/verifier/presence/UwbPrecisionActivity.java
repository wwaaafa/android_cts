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
package com.android.cts.verifier.presence;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.widget.EditText;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Activity for testing that UWB distance and angle of arrival measurements are within the right
 * range.
 */
public class UwbPrecisionActivity extends PassFailButtons.Activity {
    private static final String TAG = UwbPrecisionActivity.class.getName();
    // Report log schema
    private static final String KEY_DISTANCE_RANGE_CM = "distance_range_cm";
    private static final String KEY_AOA_RANGE_DEGREES = "aoa_range_degrees";
    private static final String KEY_REFERENCE_DEVICE = "reference_device";
    // Thresholds
    private static final int MAX_DISTANCE_RANGE_CM = 10;
    private static final int MAX_ANGLE_OF_ARRIVAL_RANGE_DEGREES = 5;

    private EditText mDistanceRangeInput;
    private EditText mAoaRangeInput;
    private EditText mReferenceDeviceInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uwb_precision);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        DeviceFeatureChecker.checkFeatureSupported(this, getPassButton(),
                PackageManager.FEATURE_UWB);

        mDistanceRangeInput = (EditText) findViewById(R.id.distance_range_cm);
        mAoaRangeInput = (EditText) findViewById(R.id.aoa_range_degrees);
        mReferenceDeviceInput = (EditText) findViewById(R.id.reference_device);
        mDistanceRangeInput.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
        mAoaRangeInput.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
        mReferenceDeviceInput.addTextChangedListener(
                InputTextHandler.getOnTextChangedHandler((Editable s) -> checkTestInputs()));
    }

    private void checkTestInputs() {
        getPassButton().setEnabled(
                checkDistanceRangeInput() && checkAoaRangeInput() && checkReferenceDeviceInput());
    }

    private boolean checkDistanceRangeInput() {
        String distanceRangeInput = mDistanceRangeInput.getText().toString();
        if (!distanceRangeInput.isEmpty()) {
            int distanceRange = Integer.parseInt(distanceRangeInput);
            // Distance range must be inputted and within acceptable range before test can be
            // passed.
            return distanceRange <= MAX_DISTANCE_RANGE_CM;
        }
        return false;
    }

    private boolean checkAoaRangeInput() {
        String aoaRangeInput = mAoaRangeInput.getText().toString();
        if (!aoaRangeInput.isEmpty()) {
            int aoaRange = Integer.parseInt(aoaRangeInput);
            // Aoa range must be within acceptable range before test can be passed.
            return aoaRange <= MAX_ANGLE_OF_ARRIVAL_RANGE_DEGREES;
        }
        return true;
    }

    private boolean checkReferenceDeviceInput() {
        // Reference device must be inputted before test can be passed.
        return !mReferenceDeviceInput.getText().toString().isEmpty();
    }

    @Override
    public void recordTestResults() {
        String distanceRange = mDistanceRangeInput.getText().toString();
        String aoaRange = mAoaRangeInput.getText().toString();
        String referenceDevice = mReferenceDeviceInput.getText().toString();
        if (!distanceRange.isEmpty()) {
            Log.i(TAG, "UWB Distance Range: " + distanceRange);
            getReportLog().addValue(KEY_DISTANCE_RANGE_CM, Integer.parseInt(distanceRange),
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }
        if (!aoaRange.isEmpty()) {
            Log.i(TAG, "UWB Angle of Arrival Range: " + aoaRange);
            getReportLog().addValue(KEY_AOA_RANGE_DEGREES, Integer.parseInt(aoaRange),
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }
        if (!referenceDevice.isEmpty()) {
            Log.i(TAG, "UWB Reference Device: " + referenceDevice);
            getReportLog().addValue(KEY_REFERENCE_DEVICE, referenceDevice,
                    ResultType.NEUTRAL, ResultUnit.NONE);
        }
        getReportLog().submit();
    }
}
