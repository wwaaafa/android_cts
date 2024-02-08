/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.Manifest.permission.MANAGE_ACCESSIBILITY;
import static android.accessibilityservice.BrailleDisplayController.BrailleDisplayCallback.FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.BrailleDisplayController;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.accessibility.Flags;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.TestUtils;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Tests for {@link BrailleDisplayController} APIs.
 */
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
@RequiresFlagsEnabled(Flags.FLAG_BRAILLE_DISPLAY_HID)
public class BrailleDisplayControllerTest {
    private static final long CALLBACK_TIMEOUT_MS = 5000;
    private static final byte[] DESCRIPTOR1 = {0xB, 0xE, 0xE, 0xF};
    private static final byte[] DESCRIPTOR2 = {0xF, 0x0, 0x0, 0xD};
    private static final String BT_ADDRESS1 = "00:11:22:33:AA:BB";
    private static final String BT_ADDRESS2 = "22:33:44:55:AA:BB";
    // Test data is in /data/system so that system_server can read/write from these files.
    // Note: this also requires using shell commands to create/read/write from files in this
    // directory, because this test app only has normal app privileges but shell can act
    // as the system user.
    private static final String TEST_DATA_DIR =
            "/data/system/" + BrailleDisplayControllerTest.class.getSimpleName();

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private final InstrumentedAccessibilityServiceTestRule<StubBrailleDisplayAccessibilityService>
            mServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubBrailleDisplayAccessibilityService.class);
    private final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule(sUiAutomation);
    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private BluetoothDevice mBluetoothDevice1;
    private BluetoothDevice mBluetoothDevice2;
    private StubBrailleDisplayAccessibilityService mService;
    private BrailleDisplayController mController;
    private Executor mExecutor;

    // Default implementation of BrailleDisplayCallback
    private static class TestBrailleDisplayCallback implements
            BrailleDisplayController.BrailleDisplayCallback {

        @Override
        public void onConnected(@NonNull byte[] hidDescriptor) {

        }

        @Override
        public void onConnectionFailed(int error) {

        }

        @Override
        public void onInput(@NonNull byte[] input) {

        }

        @Override
        public void onDisconnected() {

        }
    }

    @Rule
    public RuleChain mRuleChain = RuleChain
            .outerRule(mServiceRule)
            .around(mCheckFlagsRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    }

    @Before
    public void setup() throws Exception {
        mService = mServiceRule.getService();
        assertThat(mService).isNotNull();
        mController = mService.getBrailleDisplayController();
        mExecutor = mService.getMainExecutor();
        BluetoothManager bluetoothManager =
                sInstrumentation.getContext().getSystemService(BluetoothManager.class);
        assumeTrue(bluetoothManager != null);
        mBluetoothDevice1 = bluetoothManager.getAdapter().getRemoteDevice(BT_ADDRESS1);
        mBluetoothDevice2 = bluetoothManager.getAdapter().getRemoteDevice(BT_ADDRESS2);
        executeSystemShellCommand("mkdir " + TEST_DATA_DIR);
        TestUtils.waitUntil(TEST_DATA_DIR + " should exist", () ->
                !executeSystemShellCommand("ls -l " + TEST_DATA_DIR).isEmpty());
    }

    @After
    public void cleanup() throws Exception {
        mController.disconnect();
        executeSystemShellCommand("rm -rf " + TEST_DATA_DIR);
    }

    private void setTestData(List<Bundle> testData) {
        setTestData(mService, testData);
    }

    private static void setTestData(AccessibilityService service, List<Bundle> testData) {
        sUiAutomation.adoptShellPermissionIdentity(MANAGE_ACCESSIBILITY);
        BrailleDisplayController.setTestBrailleDisplayData(service, testData);
        sUiAutomation.dropShellPermissionIdentity();
    }

    private static Bundle getTestBrailleDisplay(String path, byte[] descriptor, String uniq,
            boolean isBluetooth) {
        Bundle bundle = new Bundle();
        bundle.putString(BrailleDisplayController.TEST_BRAILLE_DISPLAY_HIDRAW_PATH, path);
        bundle.putByteArray(BrailleDisplayController.TEST_BRAILLE_DISPLAY_DESCRIPTOR, descriptor);
        bundle.putString(BrailleDisplayController.TEST_BRAILLE_DISPLAY_UNIQUE_ID, uniq);
        bundle.putBoolean(BrailleDisplayController.TEST_BRAILLE_DISPLAY_BUS_BLUETOOTH, isBluetooth);
        return bundle;
    }

    /**
     * Runs a shell command as the {@code system} user.
     *
     * <p>Supports redirection (e.g. {@code echo hello > file}) and returns the output as a String.
     */
    private static String executeSystemShellCommand(String command) throws Exception {
        // The standard UiAutomation#executeShellCommand is implemented by Runtime#exec
        // which doesn't support using redirection.
        // This implementation works around this by executing `sh` directly, and then
        // providing the requested shell command as stdin for `sh`.
        ParcelFileDescriptor[] stdoutStdin = sUiAutomation.executeShellCommandRw("su system sh");
        ParcelFileDescriptor stdout = stdoutStdin[0];
        ParcelFileDescriptor stdin = stdoutStdin[1];
        try (OutputStream stream = new ParcelFileDescriptor.AutoCloseOutputStream(stdin)) {
            stream.write(command.getBytes());
        }
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return new String(stream.readAllBytes());
        }
    }

    private static String createTestHidrawNode(String name) throws Exception {
        String path = Path.of(TEST_DATA_DIR, name).toString();
        executeSystemShellCommand("touch " + path);
        TestUtils.waitUntil(path + " should exist", () ->
                !executeSystemShellCommand("ls " + path).isEmpty());
        return path;
    }

    private static byte[] expectConnectionSuccess(BrailleDisplayController controller,
            Executor executor, BluetoothDevice device, Consumer<byte[]> onInput,
            Runnable onDisconnected) throws Exception {
        final AtomicReference<byte[]> connectedDeviceDescriptor = new AtomicReference<>();
        BrailleDisplayController.BrailleDisplayCallback callback =
                new TestBrailleDisplayCallback() {
                    @Override
                    public void onConnected(@NonNull byte[] hidDescriptor) {
                        connectedDeviceDescriptor.set(hidDescriptor);
                    }

                    @Override
                    public void onInput(@NonNull byte[] input) {
                        if (onInput != null) {
                            onInput.accept(input);
                        }
                    }

                    @Override
                    public void onDisconnected() {
                        if (onDisconnected != null) {
                            onDisconnected.run();
                        }
                    }
                };
        controller.connect(device, executor, callback);

        TestUtils.waitUntil("Expected connection success", (int) CALLBACK_TIMEOUT_MS / 1000,
                () -> connectedDeviceDescriptor.get() != null);
        return connectedDeviceDescriptor.get();
    }

    private static byte[] expectConnectionSuccess(BrailleDisplayController controller,
            Executor executor, BluetoothDevice device) throws Exception {
        return expectConnectionSuccess(controller, executor, device, bytes -> {
        }, () -> {
        });
    }

    private static int expectConnectionFailed(BrailleDisplayController controller,
            Executor executor, BluetoothDevice device) throws Exception {
        final AtomicInteger errorCode = new AtomicInteger(0);
        BrailleDisplayController.BrailleDisplayCallback callback =
                new TestBrailleDisplayCallback() {
                    @Override
                    public void onConnectionFailed(int error) {
                        errorCode.set(error);
                    }
                };
        controller.connect(device, executor, callback);

        TestUtils.waitUntil("Expected connection failed", (int) CALLBACK_TIMEOUT_MS / 1000,
                () -> errorCode.get() != 0);
        return errorCode.get();
    }

    private static void expectFileContents(String filePath, String expectedFileContents)
            throws Exception {
        AtomicReference<String> fileContents = new AtomicReference<>();
        try {
            TestUtils.waitUntil("",
                    (int) (CALLBACK_TIMEOUT_MS / 1000),
                    () -> {
                        fileContents.set(executeSystemShellCommand("cat " + filePath));
                        return expectedFileContents.equals(fileContents.get());
                    });
        } catch (AssertionFailedError e) {
            // TestUtils.waitUntil(String, ...) requires a constant error message before failure
            // even occurs, so use a try-catch to append a more useful error message built from
            // the last known file contents after failure.
            throw new AssertionFailedError("Expected output '" + expectedFileContents
                    + "', received '" + fileContents.get() + "'\n" + e.getMessage());
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
            "android.accessibilityservice.BrailleDisplayController#isConnected",
    })
    public void connect() throws Exception {
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));

        byte[] connectedDeviceDescriptor = expectConnectionSuccess(mController, mExecutor,
                mBluetoothDevice1);

        assertThat(connectedDeviceDescriptor).isEqualTo(DESCRIPTOR1);
        assertThat(mController.isConnected()).isTrue();
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
    })
    public void connect_alreadyConnected_throwsIllegalStateException()
            throws Exception {
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1);

        assertThrows(IllegalStateException.class,
                () -> mController.connect(mBluetoothDevice1, mExecutor,
                        new TestBrailleDisplayCallback()));
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_wrongBusType_returnsNotFoundError() throws Exception {
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1,
                /*isBluetooth=*/false);
        setTestData(List.of(testBD));

        int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

        assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_wrongUniq_returnsNotFoundError() throws Exception {
        String hidraw1 = createTestHidrawNode("hidraw1");
        String wrongUniq = mBluetoothDevice1.getAddress() + "_extra";
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, wrongUniq, true);
        setTestData(List.of(testBD));

        int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

        assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
    })
    public void connect_multipleDevices_returnsCorrectDevice() throws Exception {
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD1 = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        String hidraw2 = createTestHidrawNode("hidraw2");
        Bundle testBD2 = getTestBrailleDisplay(hidraw2, DESCRIPTOR2, BT_ADDRESS2, true);
        setTestData(List.of(testBD1, testBD2));

        byte[] connectedDeviceDescriptor = expectConnectionSuccess(mController, mExecutor,
                mBluetoothDevice2);

        assertThat(connectedDeviceDescriptor).isEqualTo(DESCRIPTOR2);
        assertThat(mController.isConnected()).isTrue();
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_multipleIdenticalDevices_returnsNotFoundError() throws Exception {
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD1 = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        String hidraw2 = createTestHidrawNode("hidraw2");
        // BD2 copies all BD1 device properties, but is exposed as a different HIDRAW node path.
        Bundle testBD2 = testBD1.deepCopy();
        testBD2.putString(BrailleDisplayController.TEST_BRAILLE_DISPLAY_HIDRAW_PATH, hidraw2);
        setTestData(List.of(testBD1, testBD2));

        int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

        assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_multipleServicesSameBrailleDisplay_returnsNotFoundError() throws Exception {
        InstrumentedAccessibilityService anotherService =
                InstrumentedAccessibilityService.enableService(
                        InstrumentedAccessibilityService.class);
        try {
            String hidraw1 = createTestHidrawNode("hidraw1");
            Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
            setTestData(mService, List.of(testBD));
            setTestData(anotherService, List.of(testBD));

            // Connect another service to the Braille display first.
            expectConnectionSuccess(anotherService.getBrailleDisplayController(),
                    anotherService.getMainExecutor(), mBluetoothDevice1);
            // Attempt to connect a different service to the same Braille display.
            int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

            assertThat(anotherService.getBrailleDisplayController().isConnected()).isTrue();
            assertThat(mController.isConnected()).isFalse();
            assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
        } finally {
            anotherService.getBrailleDisplayController().disconnect();
            anotherService.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
    })
    public void connect_canConnectAfterFailedConnection() throws Exception {
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));

        expectConnectionFailed(mController, mExecutor, mBluetoothDevice2);
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1);
    }

    // TODO: b/316035785 - Change this to a CTS-Verifier test, requiring >=1 connected USB device
    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
    })
    public void connect_realUsbDevice_noPermission_throwsSecurityException() {
        // Unlike BluetoothDevice used throughout other tests, UsbDevice does not have a test
        // constructor, so we can only act on real USB devices in a test.
        //
        // It is unlikely that a CTS test environment will have a real Braille display available,
        // but we can at least test the security behavior of connect(UsbDevice) by
        // attempting to connect to any USB device that is not approved for this test app.
        //
        // All logic after the initial security check is shared between USB and Bluetooth devices.
        UsbManager usbManager = sInstrumentation.getContext().getSystemService(UsbManager.class);
        assumeTrue(usbManager != null);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        assumeFalse(deviceList.isEmpty());
        boolean foundUnapprovedDevice = false;
        for (UsbDevice usbDevice : deviceList.values()) {
            if (!usbManager.hasPermission(usbDevice)) {
                foundUnapprovedDevice = true;
                assertThrows(SecurityException.class,
                        () -> mController.connect(usbDevice, mExecutor,
                                new TestBrailleDisplayCallback()));
            }
        }
        assertThat(foundUnapprovedDevice).isTrue();
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController#disconnect",
            "android.accessibilityservice.BrailleDisplayController#isConnected",
    })
    public void connect_allowsReconnectionAfterDisconnect() throws Exception {
        // This test checks that we can reconnect after disconnection, so prepare the
        // test state by calling another test that already connects & disconnects.
        disconnect_disconnectsExistingConnection();

        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1);
        assertThat(mController.isConnected()).isTrue();
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#disconnect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onDisconnected",
    })
    public void disconnect_disconnectsExistingConnection() throws Exception {
        AtomicBoolean calledDisconnected = new AtomicBoolean();
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1, null,
                () -> calledDisconnected.set(true));

        mController.disconnect();

        TestUtils.waitUntil("Expected disconnection", (int) CALLBACK_TIMEOUT_MS / 1000,
                calledDisconnected::get);
        assertThat(mController.isConnected()).isFalse();
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#disconnect",
    })
    public void disconnect_notConnected_nothingHappens() {
        mController.disconnect();

        assertThat(mController.isConnected()).isFalse();
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onDisconnected",
    })
    public void deviceIsRemoved_callsOnBrailleDisplayDisconnected() throws Exception {
        AtomicBoolean calledDisconnected = new AtomicBoolean();
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1, null,
                () -> calledDisconnected.set(true));

        executeSystemShellCommand("rm " + hidraw1);

        TestUtils.waitUntil("Expected disconnection", (int) CALLBACK_TIMEOUT_MS / 1000,
                calledDisconnected::get);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController.BrailleDisplayCallback#onInput",
    })
    public void onInput() throws Exception {
        String input1 = "hello", input2 = "world";
        Object waitObject = new Object();
        List<byte[]> receivedInputBytes = new ArrayList<>();
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1, bytes -> {
            synchronized (waitObject) {
                receivedInputBytes.add(bytes);
                waitObject.notifyAll();
            }
        }, null);

        // Fill the Braille display "device" (test file) with two input messages
        // that arrive one second apart.
        executeSystemShellCommand("echo -n " + input1 + " >> " + hidraw1
                + " && sleep 1 && "
                + "echo -n " + input2 + " >> " + hidraw1);

        // Expect that both are individually received.
        TestUtils.waitOn(waitObject, () -> receivedInputBytes.size() == 2,
                CALLBACK_TIMEOUT_MS,
                "Expected to receive 2 calls to onInput");
        assertThat(receivedInputBytes.get(0)).isEqualTo(input1.getBytes());
        assertThat(receivedInputBytes.get(1)).isEqualTo(input2.getBytes());
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#write",
    })
    public void write() throws Exception {
        String output1 = "hello", output2 = "world";
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1);

        mController.write(output1.getBytes());
        mController.write(output2.getBytes());

        expectFileContents(hidraw1, output1 + output2);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#write",
    })
    public void write_largeOutput_throwsForLargeOutput() throws Exception {
        AtomicBoolean calledDisconnected = new AtomicBoolean();
        String regularOutput = "ABC";
        String largeOutput = "A".repeat(IBinder.getSuggestedMaxIpcSizeBytes() * 2);
        String hidraw1 = createTestHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1, null,
                () -> calledDisconnected.set(true));

        assertThrows(IllegalArgumentException.class,
                () -> mController.write(largeOutput.getBytes()));
        mController.write(regularOutput.getBytes());

        expectFileContents(hidraw1, regularOutput);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#write",
    })
    public void write_notConnected_throwsIfNotConnected() {
        assertThrows(IOException.class, () -> mController.write("hello".getBytes()));
        // No connected HIDRAW device file, so nothing to assert is empty.
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.AccessibilityService#setTestBrailleDisplayController",
            "android.accessibilityservice.AccessibilityService#clearTestBrailleDisplayController",
            "android.accessibilityservice.AccessibilityService#getBrailleDisplayController",
    })
    public void setAndClearTestBrailleDisplayController() {
        AccessibilityService service = mServiceRule.getService();
        BrailleDisplayController fakeBrailleDisplayController = new BrailleDisplayController() {
            @Override
            public void connect(@NonNull BluetoothDevice bluetoothDevice,
                    @NonNull BrailleDisplayCallback callback) {}

            @Override
            public void connect(@NonNull BluetoothDevice bluetoothDevice,
                    @NonNull Executor callbackExecutor, @NonNull BrailleDisplayCallback callback) {}

            @Override
            public void connect(@NonNull UsbDevice usbDevice,
                    @NonNull BrailleDisplayCallback callback) {}

            @Override
            public void connect(@NonNull UsbDevice usbDevice, @NonNull Executor callbackExecutor,
                    @NonNull BrailleDisplayCallback callback) {}

            @Override
            public boolean isConnected() {
                return false;
            }

            @Override
            public void write(@NonNull byte[] buffer) throws IOException {}

            @Override
            public void disconnect() {}
        };
        assertThat(fakeBrailleDisplayController).isNotEqualTo(mController);

        service.setTestBrailleDisplayController(fakeBrailleDisplayController);
        assertThat(service.getBrailleDisplayController()).isEqualTo(fakeBrailleDisplayController);

        service.clearTestBrailleDisplayController();
        assertThat(service.getBrailleDisplayController()).isEqualTo(mController);
    }
}
