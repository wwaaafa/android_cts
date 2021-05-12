/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.sensorprivacy.cts.usemiccamera

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraDevice.StateCallback
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.util.Size

private const val MIC = 1 shl 0
private const val CAM = 1 shl 1

private const val SAMPLING_RATE = 8000

private const val finishMicCamActivityAction =
        "android.sensorprivacy.cts.usemiccamera.action.FINISH_USE_MIC_CAM"
private const val useMicExtra =
        "android.sensorprivacy.cts.usemiccamera.extra.USE_MICROPHONE"
private const val useCamExtra =
        "android.sensorprivacy.cts.usemiccamera.extra.USE_CAMERA"

class UseMicCamera : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val handler = Handler(mainLooper)

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                unregisterReceiver(this)
                finish()
            }
        }, IntentFilter(finishMicCamActivityAction))

        val useMic = intent.getBooleanExtra(useMicExtra, false)
        val useCam = intent.getBooleanExtra(useCamExtra, false)
        if (useMic) {
            handler.postDelayed({ openMic() }, 1000)
        }
        if (useCam) {
            handler.postDelayed({ openCam() }, 1000)
        }
    }

    private fun openMic() {
        val audioRecord = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder()
                        .setSampleRate(SAMPLING_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                .setBufferSizeInBytes(
                        AudioRecord.getMinBufferSize(SAMPLING_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT) * 10)
                .build()

        audioRecord.startRecording()
    }

    private fun openCam() {
        val cameraManager = getSystemService(CameraManager::class.java)!!

        val cameraId = cameraManager!!.cameraIdList[0]
        val config = cameraManager!!.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputFormat = config!!.outputFormats[0]
        val outputSize: Size = config!!.getOutputSizes(outputFormat)[0]
        val handler = Handler(mainLooper)

        val cameraDeviceCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(cameraDevice: CameraDevice) {
                val imageReader = ImageReader.newInstance(
                        outputSize.width, outputSize.height, outputFormat, 2)

                val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(imageReader.surface)
                val captureRequest = builder.build()
                val sessionConfiguration = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        listOf(OutputConfiguration(imageReader.surface)),
                        mainExecutor,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.capture(captureRequest, null, handler)
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                        })

                cameraDevice.createCaptureSession(sessionConfiguration)
            }

            override fun onDisconnected(cameraDevice: CameraDevice) {}
            override fun onError(cameraDevice: CameraDevice, i: Int) {}
        }

        cameraManager!!.openCamera(cameraId, mainExecutor, cameraDeviceCallback)
    }
}