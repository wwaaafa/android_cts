/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.packageinstaller.install.cts

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_INTENT
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_PRE_APPROVAL
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE
import android.content.pm.PackageInstaller.PreapprovalDetails
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.pm.PackageManager
import android.icu.util.ULocale
import android.os.PersistableBundle
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.Until
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.FutureResultActivity
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule

open class PackageInstallerTestBase {

    companion object {
        const val TAG = "PackageInstallerTest"

        const val INSTALL_BUTTON_ID = "button1"
        const val CANCEL_BUTTON_ID = "button2"

        const val TEST_APK_NAME = "CtsEmptyTestApp.apk"
        const val TEST_APK_NAME_PL = "CtsEmptyTestApp_pl.apk"
        const val TEST_APP_LABEL = "Empty Test App"
        const val TEST_APP_LABEL_PL = "Empty Test App Polish"
        const val TEST_FAKE_APP_LABEL = "Fake Test App"
        const val TEST_APK_PACKAGE_NAME = "android.packageinstaller.emptytestapp.cts"
        const val TEST_APK_LOCATION = "/data/local/tmp/cts/packageinstaller"

        const val INSTALL_ACTION_CB = "PackageInstallerTestBase.install_cb"

        const val CONTENT_AUTHORITY = "android.packageinstaller.install.cts.fileprovider"

        const val PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller"
        const val SYSTEM_PACKAGE_NAME = "android"
        const val APP_OP_STR = "REQUEST_INSTALL_PACKAGES"

        const val PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE = "is_preapproval_available"

        const val TIMEOUT = 60000L
        const val INSTALL_INSTANT_APP = 0x00000800
    }

    @get:Rule
    val installDialogStarter = ActivityTestRule(FutureResultActivity::class.java)

    protected val context: Context = InstrumentationRegistry.getTargetContext()
    protected val pm: PackageManager = context.packageManager
    protected val pi = pm.packageInstaller
    protected val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val apkFile = File(context.filesDir, TEST_APK_NAME)
    private val apkFile_pl = File(context.filesDir, TEST_APK_NAME_PL)

    data class SessionResult(val status: Int?, val preapproval: Boolean?)

    /** If a status was received the value of the status, otherwise null */
    private var installSessionResult = LinkedBlockingQueue<SessionResult>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)
            val preapproval = intent.getBooleanExtra(EXTRA_PRE_APPROVAL, false /* defaultValue */)
            val msg = intent.getStringExtra(EXTRA_STATUS_MESSAGE)
            Log.d(TAG, "status: $status, msg: $msg")

            if (status == STATUS_PENDING_USER_ACTION) {
                val activityIntent = intent.getParcelableExtra(EXTRA_INTENT, Intent::class.java)
                activityIntent!!.addFlags(FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK)
                installDialogStarter.activity.startActivityForResult(activityIntent)
            }

            installSessionResult.offer(SessionResult(status, preapproval))
        }
    }

    @Before
    fun copyTestApk() {
        File(TEST_APK_LOCATION, TEST_APK_NAME).copyTo(target = apkFile, overwrite = true)
        File(TEST_APK_LOCATION, TEST_APK_NAME_PL).copyTo(target = apkFile_pl, overwrite = true)
    }

    @Before
    fun wakeUpScreen() {
        if (!uiDevice.isScreenOn) {
            uiDevice.wakeUp()
        }
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    @Before
    fun assertTestPackageNotInstalled() {
        try {
            context.packageManager.getPackageInfo(TEST_APK_PACKAGE_NAME, 0)
            Assert.fail("Package should not be installed")
        } catch (expected: PackageManager.NameNotFoundException) {
        }
    }

    @Before
    fun registerInstallResultReceiver() {
        context.registerReceiver(receiver, IntentFilter(INSTALL_ACTION_CB), Context.RECEIVER_EXPORTED)
    }

    @Before
    fun waitForUIIdle() {
        uiDevice.waitForIdle()
    }

    /**
     * Wait for session's install result and return it
     */
    protected fun getInstallSessionResult(timeout: Long = TIMEOUT): SessionResult {
        return installSessionResult.poll(timeout, TimeUnit.MILLISECONDS)
                ?: SessionResult(null /* status */, null /* preapproval */)
    }

    /**
     * Start an installation via a session
     */
    protected fun startInstallationViaSession(): CompletableFuture<Int> {
        return startInstallationViaSession(0 /* installFlags */)
    }

    protected fun startInstallationViaSession(installFlags: Int): CompletableFuture<Int> {
        return startInstallationViaSession(installFlags, TEST_APK_NAME)
    }

    protected fun startInstallationViaSession(appMetadata: PersistableBundle):
            CompletableFuture<Int> {
        return startInstallationViaSession(0 /* installFlags */, TEST_APK_NAME, null, appMetadata)
    }

    protected fun startInstallationViaSessionWithPackageSource(packageSource: Int?):
            CompletableFuture<Int> {
        return startInstallationViaSession(0 /* installFlags */, TEST_APK_NAME, packageSource)
    }

    protected fun createSession(
        installFlags: Int,
        isMultiPackage: Boolean,
        packageSource: Int?,
    ): Pair<Int, Session> {
        // Create session
        val sessionParam = PackageInstaller.SessionParams(MODE_FULL_INSTALL)
        // Handle additional install flags
        if (installFlags and INSTALL_INSTANT_APP != 0) {
            sessionParam.setInstallAsInstantApp(true)
        }
        if (isMultiPackage) {
            sessionParam.setMultiPackage()
        }
        if (packageSource != null) {
            sessionParam.setPackageSource(packageSource)
        }

        val sessionId = pi.createSession(sessionParam)
        val session = pi.openSession(sessionId)!!

        return Pair(sessionId, session)
    }

    protected fun writeSession(session: Session, apkName: String) {
        val apkFile = File(context.filesDir, apkName)
        // Write data to session
        apkFile.inputStream().use { fileOnDisk ->
            session.openWrite(apkName, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }
    }

    protected fun commitSession(session: Session): CompletableFuture<Int> {
        // Commit session
        val dialog = FutureResultActivity.doAndAwaitStart {
            val pendingIntent = PendingIntent.getBroadcast(
                    context, 0, Intent(INSTALL_ACTION_CB),
                    FLAG_UPDATE_CURRENT or FLAG_MUTABLE)
            session.commit(pendingIntent.intentSender)
        }

        // The system should have asked us to launch the installer
        val result = getInstallSessionResult()
        Assert.assertEquals(STATUS_PENDING_USER_ACTION, result.status)
        Assert.assertEquals(false, result.preapproval)

        return dialog
    }

    protected fun startRequestUserPreapproval(
        session: Session,
        details: PreapprovalDetails,
        expectedPrompt: Boolean = true
    ) {
        // In some abnormal cases, passing expectedPrompt as false to return immediately without
        // waiting for timeout (60 secs).
        if (!expectedPrompt) { requestSession(session, details); return }

        FutureResultActivity.doAndAwaitStart {
            requestSession(session, details)
        }

        // The system should have asked us to launch the installer
        val result = getInstallSessionResult()
        Assert.assertEquals(STATUS_PENDING_USER_ACTION, result.status)
        Assert.assertEquals(true, result.preapproval)
    }

    private fun requestSession(session: Session, details: PreapprovalDetails) {
        val pendingIntent = PendingIntent.getBroadcast(context, 0 /* requestCode */,
                Intent(INSTALL_ACTION_CB), FLAG_UPDATE_CURRENT or FLAG_MUTABLE)
        session.requestUserPreapproval(details, pendingIntent.intentSender)
    }

    protected fun startInstallationViaSession(
        installFlags: Int,
        apkName: String
    ): CompletableFuture<Int> {
        return startInstallationViaSession(installFlags, apkName, null)
    }

    protected fun startInstallationViaSession(
        installFlags: Int,
        apkName: String,
        packageSource: Int?
    ): CompletableFuture<Int> {
        val (sessionId, session) = createSession(installFlags, false, packageSource)
        writeSession(session, apkName)
        return commitSession(session)
    }

    protected fun startInstallationViaSession(
        installFlags: Int,
        apkName: String,
        packageSource: Int?,
        appMetadata: PersistableBundle?
    ): CompletableFuture<Int> {
        val (sessionId, session) = createSession(installFlags, false, packageSource)
        writeSession(session, apkName)
        try {
            session.setAppMetadata(appMetadata)
            return commitSession(session)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    protected fun startInstallationViaMultiPackageSession(
        installFlags: Int,
        vararg apkNames: String
    ): CompletableFuture<Int> {
        val (sessionId, session) = createSession(installFlags, true, null)
        for (apkName in apkNames) {
            val (childSessionId, childSession) = createSession(installFlags, false, null)
            writeSession(childSession, apkName)
            session.addChildSessionId(childSessionId)
        }
        return commitSession(session)
    }

    /**
     * Start an installation via an Intent
     */
    protected fun startInstallationViaIntent(
            intent: Intent = getInstallationIntent()
    ): CompletableFuture<Int> {
        return installDialogStarter.activity.startActivityForResult(intent)
    }

    protected fun getInstallationIntent(): Intent {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = FileProvider.getUriForFile(context, CONTENT_AUTHORITY, apkFile)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)

        return intent
    }

    protected fun startInstallationViaPreapprovalSession(session: Session) {
        val pendingIntent = PendingIntent.getBroadcast(context, 0 /* requestCode */,
                Intent(INSTALL_ACTION_CB), FLAG_UPDATE_CURRENT or FLAG_MUTABLE)
        session.commit(pendingIntent.intentSender)
    }

    fun assertInstalled() {
        // Throws exception if package is not installed.
        pm.getPackageInfo(TEST_APK_PACKAGE_NAME, 0)
    }

    fun assertNotInstalled() {
        try {
            pm.getPackageInfo(TEST_APK_PACKAGE_NAME, 0)
            Assert.fail("Package should not be installed")
        } catch (expected: PackageManager.NameNotFoundException) {
        }
    }

    /**
     * Click a button in the UI of the installer app
     *
     * @param resId The resource ID of the button to click
     */
    fun clickInstallerUIButton(resId: String) {
        val startTime = System.currentTimeMillis()
        while (startTime + TIMEOUT > System.currentTimeMillis()) {
            try {
                uiDevice.wait(Until.findObject(By.res(SYSTEM_PACKAGE_NAME, resId)), 1000).click()
                return
            } catch (ignore: Throwable) {
            }
        }
        Assert.fail("Failed to click the button: $resId")
    }

    /**
     * Sets the given secure setting to the provided value.
     */
    fun setSecureSetting(secureSetting: String, value: Int) {
        uiDevice.executeShellCommand("settings put secure $secureSetting $value")
    }

    fun setSecureFrp(secureFrp: Boolean) {
        uiDevice.executeShellCommand("settings " +
                "put global secure_frp_mode ${if (secureFrp) 1 else 0}")
    }

    @After
    fun unregisterInstallResultReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    @After
    fun uninstallTestPackage() {
        uiDevice.executeShellCommand("pm uninstall $TEST_APK_PACKAGE_NAME")
    }

    fun installTestPackage() {
        uiDevice.executeShellCommand("pm install " +
                File(TEST_APK_LOCATION, TEST_APK_NAME).canonicalPath)
    }

    protected fun preparePreapprovalDetails(): PreapprovalDetails {
        return preparePreapprovalDetails(TEST_APP_LABEL, ULocale.US, TEST_APK_PACKAGE_NAME)
    }

    protected fun preparePreapprovalDetailsInPl(): PreapprovalDetails {
        return preparePreapprovalDetails(TEST_APP_LABEL_PL, ULocale("pl"), TEST_APK_PACKAGE_NAME)
    }

    protected fun prepareWrongPreapprovalDetails(): PreapprovalDetails {
        return preparePreapprovalDetails(TEST_FAKE_APP_LABEL, ULocale.US, TEST_APK_PACKAGE_NAME)
    }

    private fun preparePreapprovalDetails(
        appLabel: String,
        locale: ULocale,
        appPackageName: String
    ): PreapprovalDetails {
        return PreapprovalDetails.Builder()
                .setLabel(appLabel)
                .setLocale(locale)
                .setPackageName(appPackageName)
                .build()
    }
}
