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
package android.uidmigration.cts

import android.Manifest.permission.INTERNET
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.permission.cts.PermissionUtils
import android.permission.cts.PermissionUtils.isPermissionGranted
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

@RunWith(AndroidJUnit4::class)
class SharedUserMigrationTest {

    companion object {
        private const val TMP_APK_PATH = "/data/local/tmp/cts/uidmigration"
        private const val RESULT_KEY = "result"
        private val NOT_AN_ERROR = Throwable()
    }

    private lateinit var mContext: Context
    private lateinit var mPm: PackageManager

    @Suppress("NOTHING_TO_INLINE")
    private inline fun <T> T?.assertNotNull(): T {
        assertNotNull(this)
        return this!!
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun assertEquals(a: Int, b: Int) = assertEquals(a.toLong(), b.toLong())

    @Before
    fun setup() {
        mContext = ApplicationProvider.getApplicationContext<Context>()
        mPm = mContext.packageManager
    }

    @After
    fun tearDown() {
        uninstallPackage(Const.INSTALL_TEST_PKG)
        uninstallPackage(Const.INSTALL_TEST_PKG + "2")
        uninstallPackage(Const.PERM_TEST_PKG)
        uninstallPackage(Const.PERM_TEST_PKG + ".secondary")
        uninstallPackage(Const.DATA_TEST_PKG)
    }

    @Test
    fun testAppInstall() {
        val apk = "$TMP_APK_PATH/InstallTestApp"
        assertTrue(installPackage("$apk.apk"))
        assertTrue(installPackage(apk + "2.apk"))

        // Both app should share the same UID.
        val uid = mPm.getPackageUid(Const.INSTALL_TEST_PKG, PackageInfoFlags.of(0))
        var pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        assertEquals(2, pkgs.size)

        // Should not allow directly removing sharedUserId.
        assertFalse(installPackage(apk + "3.apk"))

        // Leave shared UID.
        assertTrue(installPackage(apk + "4.apk"))
        pkgs = mPm.getPackagesForUid(uid).assertNotNull()
        assertEquals(1, pkgs.size)

        // Should not allow re-joining sharedUserId.
        assertFalse(installPackage("$apk.apk"))

        uninstallPackage(Const.INSTALL_TEST_PKG)
        uninstallPackage(Const.INSTALL_TEST_PKG + "2")
    }

    @Test
    fun testPermissionMigration() {
        val apk = "$TMP_APK_PATH/PermissionTestApp"
        assertTrue(installPackage(apk + "1.apk"))
        assertTrue(installPackage(apk + "2.apk"))
        val secondaryPkg = Const.PERM_TEST_PKG + ".secondary"

        // Runtime permissions are not granted by default.
        assertFalse(isPermissionGranted(secondaryPkg, WRITE_EXTERNAL_STORAGE))

        // Grant a runtime permission.
        PermissionUtils.grantPermission(secondaryPkg, WRITE_EXTERNAL_STORAGE)

        // All apps in the UID group should have the same permissions.
        assertTrue(isPermissionGranted(Const.PERM_TEST_PKG, INTERNET))
        assertTrue(isPermissionGranted(Const.PERM_TEST_PKG, WRITE_EXTERNAL_STORAGE))
        assertTrue(isPermissionGranted(secondaryPkg, INTERNET))
        assertTrue(isPermissionGranted(secondaryPkg, WRITE_EXTERNAL_STORAGE))

        // Upgrade and leave shared UID.
        assertTrue(installPackage(apk + "3.apk"))

        // The app in the original UID group should no longer have the permissions.
        assertFalse(isPermissionGranted(Const.PERM_TEST_PKG, INTERNET))
        assertFalse(isPermissionGranted(Const.PERM_TEST_PKG, WRITE_EXTERNAL_STORAGE))

        // The upgraded app should still have the permissions.
        assertTrue(isPermissionGranted(secondaryPkg, INTERNET))
        assertTrue(isPermissionGranted(secondaryPkg, WRITE_EXTERNAL_STORAGE))
        uninstallPackage(Const.PERM_TEST_PKG)
        uninstallPackage(secondaryPkg)
    }

    @Test
    fun testDataMigration() {
        val apk = "$TMP_APK_PATH/DataTestApp"
        assertTrue(installPackage(apk + "1.apk"))
        val oldUid = mPm.getPackageUid(Const.DATA_TEST_PKG, PackageInfoFlags.of(0))
        val authority = Const.DATA_TEST_PKG + ".provider"
        val resolver = mContext.contentResolver

        // Ask the app to generate a new random UUID and persist in data.
        var result = resolver.call(authority, "data", null, null).assertNotNull()
        val oldUUID = result.getString(RESULT_KEY).assertNotNull()

        val receiver = PackageBroadcastReceiver(oldUid)
        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
            mContext.registerReceiver(receiver, this)
        }
        IntentFilter().apply {
            addAction(Intent.ACTION_UID_REMOVED)
            addAction(Const.ACTION_UPDATE_ACK)
            mContext.registerReceiver(receiver, this)
        }

        // Update the data test APK and make sure UID changed.
        assertTrue(installPackage(apk + "2.apk"))
        val newUid = mPm.getPackageUid(Const.DATA_TEST_PKG, PackageInfoFlags.of(0))
        assertNotEquals(oldUid, newUid)

        // Ensure system broadcasts are delivered properly.
        try {
            val e = receiver.poll(5, TimeUnit.SECONDS)
            if (e !== NOT_AN_ERROR) {
                throw AssertionError(e)
            }
        } catch (e: InterruptedException) {
            fail(e.message)
        }
        assertEquals(newUid, receiver.newUid)
        mContext.unregisterReceiver(receiver)

        // Ask the app again for a UUID. If data migration is working, it shall be the same.
        result = resolver.call(authority, "data", null, null).assertNotNull()
        val newUUID = result.getString(RESULT_KEY)
        assertEquals(oldUUID, newUUID)
        uninstallPackage(Const.DATA_TEST_PKG)
    }

    @Test
    fun testKeyMigration() {
        val apk = "$TMP_APK_PATH/DataTestApp"
        assertTrue(installPackage(apk + "1.apk"))
        val oldUid = mPm.getPackageUid(Const.DATA_TEST_PKG, PackageInfoFlags.of(0))
        val authority = Const.DATA_TEST_PKG + ".provider"
        val resolver = mContext.contentResolver
        val secret = UUID.randomUUID().toString()

        // Ask the app to encrypt secret with AES.
        var result = resolver.call(authority, "encryptAES", secret, null).assertNotNull()
        assertNotNull(result[RESULT_KEY])
        assertNotNull(result["iv"])
        val encResult = result

        // Ask the app to generate a new EC keypair and sign our data.
        result = resolver.call(authority, "signEC", secret, null).assertNotNull()
        val signature = result.getByteArray(RESULT_KEY).assertNotNull()
        val oldCertChain = result.getByteArray("certChain").assertNotNull()

        // Update the data test APK and make sure UID changed.
        assertTrue(installPackage(apk + "2.apk"))
        val newUid = mPm.getPackageUid(Const.DATA_TEST_PKG, PackageInfoFlags.of(0))
        assertNotEquals(oldUid, newUid)

        // Ask the app to decrypt our secret.
        result = resolver.call(authority, "decryptAES", null, encResult).assertNotNull()
        val decSecret = result.getString(RESULT_KEY)
        assertEquals(secret, decSecret)

        // Ask the app to return the previously generated EC certificate.
        result = resolver.call(authority, "getECCert", null, null).assertNotNull()
        val rawCert = result.getByteArray(RESULT_KEY).assertNotNull()
        val newCertChain = result.getByteArray("certChain").assertNotNull()
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(rawCert)) as X509Certificate

        // Verify the signature and cert.
        assertArrayEquals(oldCertChain, newCertChain)
        val s = Signature.getInstance("SHA256withECDSA")
        s.initVerify(cert)
        s.update(secret.toByteArray(UTF_8))
        assertTrue(s.verify(signature))
        uninstallPackage(Const.DATA_TEST_PKG)
    }

    private fun installPackage(apkPath: String): Boolean {
        return runShellCommand("pm install --force-queryable -t $apkPath") == "Success\n"
    }

    private fun uninstallPackage(packageName: String) {
        runShellCommand("pm uninstall $packageName")
    }

    class PackageBroadcastReceiver(
        private val mPreviousUid: Int
    ) : BroadcastReceiver(), BlockingQueue<Throwable?> by ArrayBlockingQueue<Throwable?>(1) {

        var newUid = -1
            private set
        private var mCounter = 0

        override fun onReceive(context: Context?, intent: Intent) {
            try {
                verifyInternal(intent)
            } catch (e: Throwable) {
                offer(e)
            }
        }

        private fun verifyInternal(intent: Intent) {
            val action = intent.action
            assertNotNull(action)
            if (action == Intent.ACTION_UID_REMOVED) {
                // Not the test package, none of our business.
                if (intent.getIntExtra(Intent.EXTRA_UID, -1) != mPreviousUid) {
                    return
                }
            }
            val data = intent.data
            if (data != null) {
                assertEquals("package", data.scheme)
                val pkg = data.schemeSpecificPart
                assertNotNull(pkg)
                // Not the test package, none of our business.
                if (Const.DATA_TEST_PKG != pkg) {
                    return
                }
            }

            // Broadcasts must come in the following order:
            // ACTION_PACKAGE_REMOVED -> ACTION_UID_REMOVED
            // -> ACTION_PACKAGE_ADDED -> ACTION_UPDATE_ACK
            mCounter++
            when (action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    assertEquals(1, mCounter)
                    assertFalse(intent.hasExtra(Intent.EXTRA_REPLACING))
                    assertTrue(intent.getBooleanExtra(Intent.EXTRA_UID_CHANGING, false))
                    assertEquals(mPreviousUid, intent.getIntExtra(Intent.EXTRA_UID, -1))
                    newUid = intent.getIntExtra(Intent.EXTRA_NEW_UID, -1)
                    assertNotEquals(mPreviousUid, newUid)
                }
                Intent.ACTION_UID_REMOVED -> {
                    assertEquals(2, mCounter)
                    assertFalse(intent.hasExtra(Intent.EXTRA_REPLACING))
                    assertTrue(intent.getBooleanExtra(Intent.EXTRA_UID_CHANGING, false))
                    assertEquals(
                        Const.DATA_TEST_PKG,
                        intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
                    )
                }
                Intent.ACTION_PACKAGE_ADDED -> {
                    assertEquals(3, mCounter)
                    assertFalse(intent.hasExtra(Intent.EXTRA_REPLACING))
                    assertTrue(intent.getBooleanExtra(Intent.EXTRA_UID_CHANGING, false))
                    assertEquals(newUid, intent.getIntExtra(Intent.EXTRA_UID, mPreviousUid))
                    assertEquals(mPreviousUid, intent.getIntExtra(Intent.EXTRA_PREVIOUS_UID, -1))
                }
                Const.ACTION_UPDATE_ACK -> {
                    assertEquals(4, mCounter)
                    assertEquals(newUid, intent.getIntExtra(Intent.EXTRA_UID, -2))
                    // End of actions
                    offer(NOT_AN_ERROR)
                }
                Intent.ACTION_PACKAGE_REPLACED -> fail("PACKAGE_REPLACED should not be called!")
                else -> fail("Unknown action received")
            }
        }
    }
}