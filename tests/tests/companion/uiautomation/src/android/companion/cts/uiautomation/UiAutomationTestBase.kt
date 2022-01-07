package android.companion.cts.uiautomation

import android.Manifest
import android.annotation.CallSuper
import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothDeviceFilterUtils
import android.companion.CompanionDeviceManager
import android.companion.DeviceFilter
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.DEVICE_PROFILES
import android.companion.cts.common.DEVICE_PROFILE_TO_NAME
import android.companion.cts.common.DEVICE_PROFILE_TO_PERMISSION
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnAssociationCreated
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnAssociationPending
import android.companion.cts.common.RecordingCallback.CallbackMethod.OnFailure
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.TestBase
import android.companion.cts.common.assertEmpty
import android.companion.cts.common.setSystemProp
import android.content.Intent
import android.net.MacAddress
import android.os.Parcelable
import androidx.test.uiautomator.UiDevice
import org.junit.Assume
import org.junit.Assume.assumeFalse
import java.util.regex.Pattern
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

open class UiAutomationTestBase(
    protected val profile: String?,
    private val profilePermission: String?
) : TestBase() {
    private val uiDevice: UiDevice by lazy { UiDevice.getInstance(instrumentation) }
    protected val confirmationUi by lazy { CompanionDeviceManagerUi(uiDevice) }
    protected val callback by lazy { RecordingCallback() }

    @CallSuper
    override fun tearDown() {
        super.tearDown()

        CompanionActivity.safeFinish()
        confirmationUi.dismiss()

        restoreDiscoveryTimeout()
    }

    @CallSuper
    override fun setUp() {
        super.setUp()

        assumeFalse(confirmationUi.isVisible)
        Assume.assumeTrue(CompanionActivity.waitUntilGone())
        uiDevice.waitForIdle()

        callback.clearRecordedInvocations()
    }

    protected fun test_userRejected(
        singleDevice: Boolean = false,
        selfManaged: Boolean = false,
        displayName: String? = null
    ) = test_cancelled(singleDevice, selfManaged, displayName) {
            // User "rejects" the request.
            confirmationUi.clickNegativeButton()
        }

    protected fun test_userDismissed(
        singleDevice: Boolean = false,
        selfManaged: Boolean = false,
        displayName: String? = null
    ) = test_cancelled(singleDevice, selfManaged, displayName) {
            // User "dismisses" the request.
            uiDevice.pressBack()
        }

    private fun test_cancelled(
        singleDevice: Boolean,
        selfManaged: Boolean,
        displayName: String?,
        cancelAction: () -> Unit
    ) {
        sendRequestAndLaunchConfirmation(singleDevice, selfManaged, displayName)

        callback.assertInvokedByActions {
            cancelAction()
        }
        // Check callback invocations: there should have been exactly 1 invocation of the
        // onFailure() method.
        callback.invocations.let {
            assertEquals(actual = it.size, expected = 1)
            assertEquals(actual = it[0].method, expected = OnFailure)
            assertEquals(actual = it[0].error, expected = "Cancelled.")
        }

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code delivered via onActivityResult()
        val (resultCode: Int, _) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = Activity.RESULT_CANCELED)

        // Make sure no Associations were created.
        assertEmpty(cdm.myAssociations)
    }

    protected fun test_timeout(singleDevice: Boolean = false) {
        setDiscoveryTimeout(1.seconds)

        // The discovery timeout is 1 sec, but let's give it 2.
        callback.assertInvokedByActions(2.seconds) {
            // Make sure no device will match the request
            sendRequestAndLaunchConfirmation(
                singleDevice = singleDevice,
                deviceFilter = UNMATCHABLE_BT_FILTER
            )
        }

        // Check callback invocations: there should have been exactly 1 invocation of the
        // onFailure() method.
        callback.invocations.let {
            assertEquals(actual = it.size, expected = 1)
            assertEquals(actual = it[0].method, expected = OnFailure)
            assertEquals(actual = it[0].error, expected = "Timeout.")
        }

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code delivered via onActivityResult()
        val (resultCode: Int, _) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = Activity.RESULT_CANCELED)

        // Make sure no Associations were created.
        assertEmpty(cdm.myAssociations)
    }

    protected fun test_userConfirmed_foundDevice(
        singleDevice: Boolean,
        confirmationAction: () -> Unit
    ) {
        sendRequestAndLaunchConfirmation(singleDevice = singleDevice)

        callback.assertInvokedByActions {
            confirmationAction()
        }
        // Check callback invocations: there should have been exactly 1 invocation of the
        // OnAssociationCreated() method.
        callback.invocations.let {
            assertEquals(actual = it.size, expected = 1)
            assertEquals(actual = it[0].method, expected = OnAssociationCreated)
            assertNotNull(it[0].associationInfo)
        }
        val associationFromCallback = callback.invocations[0].associationInfo

        // Wait until the Confirmation UI goes away.
        confirmationUi.waitUntilGone()

        // Check the result code and the data delivered via onActivityResult()
        val (resultCode: Int, data: Intent?) = CompanionActivity.waitForActivityResult()
        assertEquals(actual = resultCode, expected = Activity.RESULT_OK)
        assertNotNull(data)
        val associationFromActivityResult: AssociationInfo? =
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION)
        assertNotNull(associationFromActivityResult)
        // Check that the association reported back via the callback same as the association
        // delivered via onActivityResult().
        assertEquals(associationFromCallback, associationFromActivityResult)

        // Make sure "device data" was included (for backwards compatibility), and that the
        // MAC address extracted from this data matches the MAC address from AssociationInfo.
        val deviceFromActivityResult: Parcelable? =
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        assertNotNull(deviceFromActivityResult)

        val deviceMacAddress =
                BluetoothDeviceFilterUtils.getDeviceMacAddress(deviceFromActivityResult)
        assertEquals(actual = MacAddress.fromString(deviceMacAddress),
                expected = associationFromCallback.deviceMacAddress)

        // Make sure getMyAssociations() returns the same association we received via the callback
        // as well as in onActivityResult()
        assertContentEquals(actual = cdm.myAssociations, expected = listOf(associationFromCallback))
    }

    protected fun sendRequestAndLaunchConfirmation(
        singleDevice: Boolean = false,
        selfManaged: Boolean = false,
        displayName: String? = null,
        deviceFilter: DeviceFilter<*>? = null
    ) {
        val request = AssociationRequest.Builder()
                .apply {
                    // Set the single-device flag.
                    setSingleDevice(singleDevice)

                    // Set the self-managed flag.
                    setSelfManaged(selfManaged)

                    // Set profile if not null.
                    profile?.let { setDeviceProfile(it) }

                    // Set display name if not null.
                    displayName?.let { setDisplayName(it) }

                    // Add device filter if not null.
                    deviceFilter?.let { addDeviceFilter(it) }
                }
                .build()
        callback.clearRecordedInvocations()

        callback.assertInvokedByActions {
            // If the REQUEST_COMPANION_SELF_MANAGED and/or the profile permission is required:
            // run with these permissions as the Shell;
            // otherwise: just call associate().
            with(getRequiredPermissions(selfManaged)) {
                if (isNotEmpty()) {
                    withShellPermissionIdentity(*toTypedArray()) {
                        cdm.associate(request, SIMPLE_EXECUTOR, callback)
                    }
                } else {
                    cdm.associate(request, SIMPLE_EXECUTOR, callback)
                }
            }
        }
        // Check callback invocations: there should have been exactly 1 invocation of the
        // onAssociationPending() method.
        callback.invocations.let {
            assertEquals(actual = it.size, expected = 1)
            assertEquals(actual = it[0].method, expected = OnAssociationPending)
            assertNotNull(it[0].intentSender)
        }

        // Get intent sender and clear callback invocations.
        val pendingConfirmation = callback.invocations[0].intentSender
        callback.clearRecordedInvocations()

        // Launch CompanionActivity, and then launch confirmation UI from it.
        CompanionActivity.launchAndWait(context)
        CompanionActivity.startIntentSender(pendingConfirmation)

        confirmationUi.waitUntilVisible()
    }

    private fun getRequiredPermissions(selfManaged: Boolean): List<String> =
            mutableListOf<String>().also {
                if (selfManaged) it += Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
                if (profilePermission != null) it += profilePermission
            }

    private fun setDiscoveryTimeout(timeout: Duration) =
        instrumentation.setSystemProp(
            SYS_PROP_DEBUG_TIMEOUT,
            timeout.inWholeMilliseconds.toString()
        )

    private fun restoreDiscoveryTimeout() = setDiscoveryTimeout(ZERO)

    companion object {
        /**
         * List of (profile, permission, name) tuples that represent all supported profiles and
         * null.
         */
        @JvmStatic
        protected fun supportedProfilesAndNull() = mutableListOf<Array<String?>>().apply {
            add(arrayOf(null, null, "null"))
            addAll(supportedProfiles())
        }

        /** List of (profile, permission, name) tuples that represent all supported profiles. */
        private fun supportedProfiles(): Collection<Array<String?>> = DEVICE_PROFILES.map {
            profile ->
            arrayOf(profile,
                    DEVICE_PROFILE_TO_PERMISSION[profile]!!,
                    DEVICE_PROFILE_TO_NAME[profile]!!)
        }

        private val UNMATCHABLE_BT_FILTER = BluetoothDeviceFilter.Builder()
                .setAddress("FF:FF:FF:FF:FF:FF")
                .setNamePattern(Pattern.compile("This Device Does Not Exist"))
                .build()

        private const val SYS_PROP_DEBUG_TIMEOUT = "debug.cdm.discovery_timeout"
    }
}