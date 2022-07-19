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

package android.companion.cts.uiautomation

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.CompanionException
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.content.Intent
import android.os.OutcomeReceiver
import android.os.SystemClock
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import libcore.util.EmptyArray
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the system data transfer.
 *
 * Build/Install/Run: atest CtsCompanionDeviceManagerUiAutomationTestCases:SystemDataTransferTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class SystemDataTransferTest : UiAutomationTestBase(null, null) {
    companion object {
        private const val MESSAGE_RESPONSE_SUCCESS = 0x33838567
        private const val MESSAGE_RESPONSE_FAILURE = 0x33706573

        private const val SYSTEM_DATA_TRANSFER_RESPONSE_DELAY = 5_000L // Wait 5 seconds
        private const val SYSTEM_DATA_TRANSFER_TIMEOUT = 10_000L // 10 seconds
    }

    @Test
    fun test_userConsentDialogAllowed() {
        val association1 = associate()

        // First time request permission transfer should prompt a dialog
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNotNull(pendingUserConsent)
        CompanionActivity.startIntentSender(pendingUserConsent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        confirmationUi.clickPositiveButton()
        val (resultCode: Int, _: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(expected = RESULT_OK, actual = resultCode)

        // Second time request permission transfer should get null IntentSender
        val pendingUserConsent2 = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNull(pendingUserConsent2)

        // disassociate() should clean up the requests
        cdm.disassociate(association1.id)
        Thread.sleep(2_100)
        val association2 = associate()
        val pendingUserConsent3 = cdm.buildPermissionTransferUserConsentIntent(association2.id)
        assertNotNull(pendingUserConsent3)
    }

    @Test
    fun test_userConsentDialogDisallowed() {
        val association1 = associate()

        // First time request permission transfer should prompt a dialog
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNotNull(pendingUserConsent)
        CompanionActivity.startIntentSender(pendingUserConsent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        confirmationUi.clickNegativeButton()
        val (resultCode: Int, _: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(expected = RESULT_CANCELED, actual = resultCode)

        // Second time request permission transfer should get null IntentSender
        val pendingUserConsent2 = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNull(pendingUserConsent2)

        // disassociate() should clean up the requests
        cdm.disassociate(association1.id)
        Thread.sleep(2_100)
        val association2 = associate()
        val pendingUserConsent3 = cdm.buildPermissionTransferUserConsentIntent(association2.id)
        assertNotNull(pendingUserConsent3)
    }

    @Test
    fun test_userConsentDialogCanceled() {
        val association1 = associate()

        // First time request permission transfer should prompt a dialog
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNotNull(pendingUserConsent)
        CompanionActivity.startIntentSender(pendingUserConsent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        uiDevice.pressBack()

        // Second time request permission transfer should prompt a dialog
        val pendingUserConsent2 = cdm.buildPermissionTransferUserConsentIntent(association1.id)
        assertNotNull(pendingUserConsent2)
    }

    @Test(expected = CompanionException::class)
    fun test_startSystemDataTransfer_requiresUserConsent() {
        CompanionActivity.launchAndWait(context)

        // Create a self-managed association so we aren't sending data to a random device
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        val bytes = generateResponse(success = true, "SUCCESS")
        val input = ByteArrayInputStream(bytes)
        val output = ByteArrayOutputStream()

        // This will fail due to lack of user consent
        startSystemDataTransfer(associationId, input, output)
    }

    @Test
    @Ignore("Null passed in for a non-null parameter. See b/239455439")
    fun test_startSystemDataTransfer_success() {
        CompanionActivity.launchAndWait(context)

        // Create a self-managed association so we aren't sending data to a random device
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        // First time request permission transfer should prompt a dialog
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(associationId)
        assertNotNull(pendingUserConsent)
        CompanionActivity.startIntentSender(pendingUserConsent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        confirmationUi.clickPositiveButton()
        val (resultCode: Int, _: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(expected = RESULT_OK, actual = resultCode)

        // Generate data packet with successful response
        val bytes = generateResponse(success = true, "SUCCESS")
        val input = ByteArrayInputStream(bytes)
        val output = ByteArrayOutputStream()

        startSystemDataTransfer(associationId, input, output)
    }

    @Test(expected = CompanionException::class)
    fun test_startSystemDataTransfer_failure() {
        CompanionActivity.launchAndWait(context)

        // Create a self-managed association so we aren't sending data to a random device
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        // First time request permission transfer should prompt a dialog
        val pendingUserConsent = cdm.buildPermissionTransferUserConsentIntent(associationId)
        assertNotNull(pendingUserConsent)
        CompanionActivity.startIntentSender(pendingUserConsent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        confirmationUi.clickPositiveButton()
        val (resultCode: Int, _: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(expected = RESULT_OK, actual = resultCode)

        // Generate data packet with failure as response
        val bytes = generateResponse(success = false, "FAILURE")
        val input = ByteArrayInputStream(bytes)
        val output = ByteArrayOutputStream()

        // Delay input so that CDM can first send permission restore data before receiving result
        startSystemDataTransfer(associationId, input, output)
    }

    private fun generateResponse(success: Boolean, data: String? = null): ByteArray {
        val bytes = data?.toByteArray(StandardCharsets.UTF_8) ?: EmptyArray.BYTE
        val message = if (success) MESSAGE_RESPONSE_SUCCESS else MESSAGE_RESPONSE_FAILURE

        // Construct data packet with header + data
        return ByteBuffer.allocate(bytes.size + 12)
                .putInt(message) // message type
                .putInt(1) // message sequence
                .putInt(bytes.size) // data size
                .put(bytes) // actual data
                .array()
    }

    /**
     * Associate without checking the association data.
     */
    private fun associate(): AssociationInfo {
        sendRequestAndLaunchConfirmation()
        callback.assertInvokedByActions {
            confirmationUi.waitAndClickOnFirstFoundDevice()
        }
        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()
        // Check the result code and the data delivered via onActivityResult()
        val (_: Int, associationData: Intent?) = CompanionActivity.waitForActivityResult()
        CompanionActivity.clearResult()
        assertNotNull(associationData)
        val association: AssociationInfo? = associationData.getParcelableExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java)
        assertNotNull(association)

        return association
    }

    /**
     * Start system data transfer synchronously.
     */
    private fun startSystemDataTransfer(
            associationId: Int,
            input: InputStream,
            output: OutputStream
    ) {
        // Delay input stream so that CDM has a chance to send permission restore data first.
        val delayedInput = DelayedInputStream(input, SYSTEM_DATA_TRANSFER_RESPONSE_DELAY)
        cdm.attachSystemDataTransport(associationId, delayedInput, output)

        // Synchronously start system data transfer
        val latch = CountDownLatch(1)
        val err = AtomicReference<CompanionException>()
        val callback = object : OutcomeReceiver<Void, CompanionException> {
            override fun onResult(result: Void) {
                latch.countDown()
            }

            override fun onError(error: CompanionException) {
                err.set(error)
                latch.countDown()
            }
        }
        cdm.startSystemDataTransfer(associationId, context.mainExecutor, callback)

        // Don't let it hang for too long!
        if (!latch.await(SYSTEM_DATA_TRANSFER_TIMEOUT, TimeUnit.SECONDS)) {
            throw TimeoutException("System data transfer timed out.")
        }

        // Catch transfer failure
        if (err.get() != null) {
            throw err.get()
        }

        // Detach data transport
        cdm.detachSystemDataTransport(associationId)
    }

    /**
     * Input stream that waits for specified amount of time before reading.
     */
    private class DelayedInputStream(
            input: InputStream,
            private val delay: Long
    ) : FilterInputStream(input) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            SystemClock.sleep(delay)
            return super.read(b, off, len)
        }
    }
}
