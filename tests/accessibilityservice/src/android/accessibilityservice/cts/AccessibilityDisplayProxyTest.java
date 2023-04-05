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

package android.accessibilityservice.cts;

import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.MANAGE_ACCESSIBILITY;
import static android.Manifest.permission.WAKE_LOCK;
import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
import static android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterForEventType;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWaitForAll;
import static android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils.filterWindowsChangedWithChangeTypes;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.findWindowByTitleWithList;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.getActivityTitle;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.supportsMultiDisplay;
import static android.accessibilityservice.cts.utils.MultiProcessUtils.SEPARATE_PROCESS_ACTIVITY_TITLE;
import static android.accessibilityservice.cts.utils.MultiProcessUtils.TOUCH_EXPLORATION_CHANGE_EVENT_TEXT;
import static android.accessibilityservice.cts.utils.WindowCreationUtils.TOP_WINDOW_TITLE;
import static android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES;
import static android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ACTIVE;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_FOCUSED;
import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS;
import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.compatibility.common.util.TestUtils.waitOn;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibility.cts.common.ShellCommandBuilder;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.NonProxyActivity;
import android.accessibilityservice.cts.activities.ProxyDisplayActivity;
import android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils;
import android.accessibilityservice.cts.utils.DisplayUtils;
import android.accessibilityservice.cts.utils.WindowCreationUtils;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityDisplayProxy;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.AccessibilityServicesStateChangeListener;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Ensure AccessibilityDisplayProxy APIs work.
 *
 * <p>
 * Note: AccessibilityDisplayProxy is in android.view.accessibility since apps take advantage of it.
 * AccessibilityDisplayProxyTest is in android.accessibilityservice.cts, not
 * android.view.accessibility.cts, since the proxy behaves likes an a11y service and this package
 * gives access to a suite of helpful utils for testing service-like behavior.
 * <p>
 * This class tests a variety of interactions:
 * <ul>
 *     <li> When consumers of accessibility data for a display registers or unregisters an
 *     AccessibilityDisplayProxy. They must have the MANAGE_ACCESSIBILITY or
 *     CREATE_VIRTUAL_DEVICE permission.
 *     <li> When AccessibilityDisplayProxy uses its class APIs.
 *     <li> When a service and proxy are active at the same time. An AccessibilityServices should
 *     not have access to a proxy-ed display's data and vice versa.
 *     <li> When a service and proxy are active and want accessibility focus. These should be
 *     separate focuses.
 *     <li> When apps are notified of changes to accessibility state. An app belonging to a
 *     virtual device that has a registered proxy should maintain different a11y state than that on
 *     the phone. There is one AccessibilityManager per process, so to test these notifications we
 *     must have different processes (associated with different devices). So we spin up a separate
 *     app outside of the instrumentation process. This prevents direct access to the app's
 *     components like Activities, so we test state changes via AccessibilityEvents. In the
 *     long-term, we should be able to modify these tests and access the Activities directly.
 * </ul>
 *
 * Note: NonProxyActivity and NonProxySeparateAppActivity both exist because NonProxyActivity
 * belongs to the instrumentation process, meaning its AccessibilityManager will reflect the same
 * state as the proxy. Accessibility focus separation tests can use NonProxyActivity since touch
 * exploration is required for both the proxy and the service. NonProxySeparateAppActivity is
 * needed to test different AccessibilityManager states.
 * TODO(271639633): Test a11y is enabled/disabled, potentially focus appearance changes and event
 * types changes for the non-proxy app. (This will be easier in the long term when a11y managers are
 * split within processes and we can directly access them here.)
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@Presubmit
public class AccessibilityDisplayProxyTest {
    private static final int INVALID_DISPLAY_ID = 10000;
    private static final int TIMEOUT_MS = 5000;

    private static final int NON_INTERACTIVE_UI_TIMEOUT = 100;
    private static final int INTERACTIVE_UI_TIMEOUT = 200;
    private static final int NOTIFICATION_TIMEOUT = 100;
    private static final String PACKAGE_1 = "package 1";
    private static final String PACKAGE_2 = "package 2";
    private static final int NON_PROXY_SERVICE_TIMEOUT = 20000;

    private static final String SEPARATE_PROCESS_PACKAGE_NAME = "foo.bar.proxy";
    private static final String SEPARATE_PROCESS_ACTIVITY = ".NonProxySeparateAppActivity";

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private static String sEnabledServices;

    // The manager representing the app registering/unregistering the proxy.
    private AccessibilityManager mA11yManager;

    // This is technically the same manager as mA11yManager, since there is one manager per process,
    // but add separation for readability.
    private AccessibilityManager mProxyActivityA11yManager;
    private MyA11yProxy mA11yProxy;
    private int mVirtualDisplayId;
    private VirtualDisplay mVirtualDisplay;
    private Activity mProxiedVirtualDisplayActivity;
    private CharSequence mProxiedVirtualDisplayActivityTitle;

    // Activity used for checking accessibility and input focus behavior. An activity in a separate
    // process is not required, since touch exploration is enabled for both the proxy and non-proxy
    // i.e. the AccessibilityManagers have the same state.
    private Activity mNonProxiedConcurrentActivity;

    private Intent mSeparateProcessActivityIntent = new Intent(Intent.ACTION_MAIN)
                .setClassName(SEPARATE_PROCESS_PACKAGE_NAME,
            SEPARATE_PROCESS_PACKAGE_NAME + SEPARATE_PROCESS_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);;

    // Virtual Device variables.
    private VirtualDeviceManager mVirtualDeviceManager;
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;

    private ImageReader mImageReader;
    private static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();
    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private InstrumentedAccessibilityServiceTestRule<StubProxyConcurrentAccessibilityService>
            mNonProxyServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubProxyConcurrentAccessibilityService.class, false);

    private final ActivityTestRule<NonProxyActivity> mNonProxyActivityRule =
            new ActivityTestRule<>(NonProxyActivity.class, false, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mNonProxyServiceRule)
            .around(mNonProxyActivityRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        // Save enabled accessibility services before disabling them so they can be re-enabled after
        // the test.
        sEnabledServices = Settings.Secure.getString(
                sInstrumentation.getContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        // Disable all services before enabling Accessibility service to prevent flakiness
        // that depends on which services are enabled.
        InstrumentedAccessibilityService.disableAllServices();

        sUiAutomation =
                sInstrumentation.getUiAutomation(FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        final AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        ShellCommandBuilder.create(sInstrumentation)
                .putSecureSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, sEnabledServices)
                .run();
    }

    @Before
    public void setUp() throws Exception {
        final Context context = sInstrumentation.getContext();
        assumeTrue(supportsMultiDisplay(context));
        mA11yManager = context.getSystemService(AccessibilityManager.class);
        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
        mVirtualDisplay = createVirtualDeviceAndLaunchVirtualDisplay();
        assertThat(mVirtualDisplay).isNotNull();
        mVirtualDisplayId = mVirtualDisplay.getDisplay().getDisplayId();
        final List<AccessibilityServiceInfo> infos = new ArrayList<>();
        final AccessibilityServiceInfo proxyInfo = new AccessibilityServiceInfo();
        proxyInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        proxyInfo.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        infos.add(proxyInfo);
        mA11yProxy = new MyA11yProxy(mVirtualDisplayId, Executors.newSingleThreadExecutor(), infos);
        mProxiedVirtualDisplayActivity = launchActivityOnVirtualDisplay(
                mVirtualDisplay.getDisplay().getDisplayId());
        mProxiedVirtualDisplayActivityTitle = getActivityTitle(sInstrumentation,
                mProxiedVirtualDisplayActivity);
        mProxyActivityA11yManager =
                mProxiedVirtualDisplayActivity.getSystemService(AccessibilityManager.class);
    }

    @After
    public void tearDown() throws TimeoutException {
        sUiAutomation.adoptShellPermissionIdentity(
                MANAGE_ACCESSIBILITY, CREATE_VIRTUAL_DEVICE, WAKE_LOCK);
        if (mA11yProxy != null) {
            mA11yManager.unregisterDisplayProxy(mA11yProxy);
        }
        if (mProxiedVirtualDisplayActivity != null) {
            mProxiedVirtualDisplayActivity.runOnUiThread(
                    () -> mProxiedVirtualDisplayActivity.finish());
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
        if (mImageReader != null) {
            mImageReader.close();
        }
        if (mNonProxiedConcurrentActivity != null)      {
            mNonProxiedConcurrentActivity.runOnUiThread(
                    () -> mNonProxiedConcurrentActivity.finish());
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerDisplayProxy"})
    public void testRegisterDisplayProxy_withPermission_successfullyRegisters() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThat(mA11yManager.registerDisplayProxy(mA11yProxy)).isTrue(),
                CREATE_VIRTUAL_DEVICE, MANAGE_ACCESSIBILITY);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerDisplayProxy"})
    public void testRegisterDisplayProxy_withoutPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                mA11yManager.registerDisplayProxy(mA11yProxy));
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerDisplayProxy"})
    public void testRegisterDisplayProxy_withoutA11yPermission_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThrows(SecurityException.class, () ->
                mA11yManager.registerDisplayProxy(mA11yProxy)), CREATE_VIRTUAL_DEVICE);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerDisplayProxy"})
    public void testRegisterDisplayProxy_withoutDevicePermission_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThrows(SecurityException.class, () ->
                        mA11yManager.registerDisplayProxy(mA11yProxy)), MANAGE_ACCESSIBILITY);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerDisplayProxy"})
    public void testRegisterDisplayProxy_alreadyProxied_throwsIllegalArgumentException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThat(mA11yManager.registerDisplayProxy(mA11yProxy)).isTrue());

        runWithShellPermissionIdentity(sUiAutomation, () ->
                assertThrows(IllegalArgumentException.class, () ->
                        mA11yManager.registerDisplayProxy(mA11yProxy)));
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#registerDisplayProxy"})
    public void testRegisterAccessibilityProxy_withDefaultDisplay_throwsIllegalArgException() {
        final MyA11yProxy invalidProxy = new MyA11yProxy(
                Display.DEFAULT_DISPLAY, Executors.newSingleThreadExecutor(), new ArrayList<>());
        try {
            runWithShellPermissionIdentity(sUiAutomation, () ->
                    assertThrows(IllegalArgumentException.class, () ->
                    mA11yManager.registerDisplayProxy(invalidProxy)));
        } finally {
            runWithShellPermissionIdentity(sUiAutomation, () ->
                    mA11yManager.unregisterDisplayProxy(invalidProxy));
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#unregisterDisplayProxy"})
    public void testUnregisterDisplayProxy_withoutDevicePermission_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThrows(SecurityException.class, () ->
                        mA11yManager.unregisterDisplayProxy(mA11yProxy)), MANAGE_ACCESSIBILITY);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#unregisterDisplayProxy"})
    public void testUnregisterDisplayProxy_withoutA11yPermission_throwsSecurityException() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> assertThrows(SecurityException.class, () ->
                        mA11yManager.unregisterDisplayProxy(mA11yProxy)), CREATE_VIRTUAL_DEVICE);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#unregisterDisplayProxy"})
    public void testUnregisterDisplayProxy_withPermission_successfullyUnregisters() {
        runWithShellPermissionIdentity(sUiAutomation, () -> {
            assertThat(mA11yManager.registerDisplayProxy(mA11yProxy)).isTrue();
            assertThat(mA11yManager.unregisterDisplayProxy(mA11yProxy)).isTrue();
        });
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager#unregisterDisplayProxy"})
    public void testUnregisterDisplayProxy_withPermission_failsToUnregister() {
        final MyA11yProxy invalidProxy = new MyA11yProxy(
                INVALID_DISPLAY_ID, Executors.newSingleThreadExecutor(), new ArrayList<>());
        runWithShellPermissionIdentity(sUiAutomation, () -> {
            assertThat(mA11yManager.unregisterDisplayProxy(invalidProxy)).isFalse();
        });
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#getDisplayId"})
    public void testGetDisplayId_always_returnsId() {
        assertThat(mA11yProxy.getDisplayId()).isEqualTo(mVirtualDisplayId);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testOnAccessibilityEvent_clickButton_proxyReceivesClickEvent() {
        registerProxyAndWaitForConnection();
        // Create and populate the expected event
        AccessibilityEvent clickEvent = getProxyClickAccessibilityEvent();

        mA11yProxy.setEventFilter(getClickEventFilter(clickEvent));

        final Button button = mProxiedVirtualDisplayActivity.findViewById(R.id.button);
        mProxiedVirtualDisplayActivity.runOnUiThread(() -> button.performClick());

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(), TIMEOUT_MS,
                "Click event received");
    }

    @Test
    public void testOnAccessibilityEvent_clickButton_serviceDoesNotReceiveClickEvent() {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            // Create and populate the expected event.
            AccessibilityEvent clickEvent = getProxyClickAccessibilityEvent();
            service.setEventFilter(getClickEventFilter(clickEvent));

            final Button button = mProxiedVirtualDisplayActivity.findViewById(R.id.button);
            mProxiedVirtualDisplayActivity.runOnUiThread(() -> button.performClick());
            assertThrows(AssertionError.class, () ->
                    waitOn(service.mWaitObject, ()-> service.mReceivedEvent.get(),
                            TIMEOUT_MS,
                    "Expected event was not received within " + TIMEOUT_MS + " ms"));
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#getWindows"})
    public void testGetWindows_always_proxyReceivesWindowsOnDisplay() {
        registerProxyAndWaitForConnection();
        assertVirtualDisplayActivityExistsToProxy();
    }

    @Test
    @ApiTest(apis = {"android.accessibilityservice.AccessibilityService#getWindows"})
    public void testGetWindows_always_serviceDoesNotGetProxyWindows() {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            SparseArray<List<AccessibilityWindowInfo>> windowsOnAllDisplays =
                    service.getWindowsOnAllDisplays();
            assertThat(windowsOnAllDisplays.contains(mVirtualDisplayId)).isFalse();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_always_proxyGetsAccessibilityFocus() throws TimeoutException {
        registerProxyAndEnableTouchExploration();

        final EditText proxyEditText = mProxiedVirtualDisplayActivity.findViewById(R.id.edit_text);
        setAccessibilityFocus(proxyEditText);

        final AccessibilityNodeInfo a11yFocusedNode = mA11yProxy.findFocus(
                AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        assertThat(a11yFocusedNode).isEqualTo(proxyEditText.createAccessibilityNodeInfo());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_serviceSetsAccessibilityFocus_proxyGetsNullFocus() throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyAndEnableTouchExploration();
            // Launch an activity on the default display.
            mNonProxiedConcurrentActivity = launchProxyConcurrentActivityOnDefaultDisplay(service);
            final EditText serviceEditText = mNonProxiedConcurrentActivity.findViewById(
                    R.id.editText);
            setAccessibilityFocus(serviceEditText);

            final AccessibilityNodeInfo a11yFocusedNode = service.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            assertThat(a11yFocusedNode).isEqualTo(serviceEditText.createAccessibilityNodeInfo());
            assertThat(mA11yProxy.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)).isNull();
        } finally {
            service.disableSelfAndRemove();
        }
    }
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxySetsAccessibilityFocus_serviceGetsNullFocus() throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyAndEnableTouchExploration();

            final EditText proxyEditText =
                    mProxiedVirtualDisplayActivity.findViewById(R.id.edit_text);
            setAccessibilityFocus(proxyEditText);

            final AccessibilityNodeInfo a11yFocusedNode = mA11yProxy.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            assertThat(a11yFocusedNode).isEqualTo(proxyEditText.createAccessibilityNodeInfo());

            assertThat(service.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)).isNull();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testOnA11yEvent_moveFocusWithinWindow_proxyDoesNotGetWindowEvent() {
        registerProxyAndEnableTouchExploration();

        final EditText editText = mProxiedVirtualDisplayActivity.findViewById(R.id.edit_text);
        // Avoid using setAccessibilityFocus/waiting for events on UiAutomation's thread, since
        // the WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED event from the EditText may be captured by the
        // filter below.
        setInitialAccessibilityFocusAndWaitForProxyEvents(editText);

        mA11yProxy.setEventFilter(
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED));
        final Button button = mProxiedVirtualDisplayActivity.findViewById(R.id.button);
        mProxiedVirtualDisplayActivity.runOnUiThread(() -> button.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));

        assertThrows("A WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED event was received.",
                AssertionError.class, () ->
                        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(),
                                TIMEOUT_MS, "(expected to timeout)"));
        assertThat(button.isAccessibilityFocused()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testOnA11yEvent_setInitialFocus_proxyGetsWindowEvent() {
        registerProxyAndEnableTouchExploration();

        mA11yProxy.setEventFilter(
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED));
        final EditText editText = mProxiedVirtualDisplayActivity.findViewById(R.id.edit_text);
        mProxiedVirtualDisplayActivity.runOnUiThread(() -> editText.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(), TIMEOUT_MS,
                "WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED received");
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testOnA11yEvent_moveFocusBetweenWindows_proxyGetsWindowEvent() throws Exception {
        registerProxyAndEnableTouchExploration();

        final EditText editText = mProxiedVirtualDisplayActivity.findViewById(R.id.edit_text);
        setInitialAccessibilityFocusAndWaitForProxyEvents(editText);

        final View topWindowView = showTopWindowAndWaitForItToShowUp();
        final AccessibilityWindowInfo topWindow = findWindowByTitleWithList(TOP_WINDOW_TITLE,
                mA11yProxy.getWindows());
        assertThat(topWindow).isNotNull();

        mA11yProxy.setEventFilter(filterWaitForAll(
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED),
                filterForEventType(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)));
        final AccessibilityNodeInfo buttonNode =
                topWindow.getRoot().findAccessibilityNodeInfosByText(
                        sInstrumentation.getContext().getString(R.string.button1)).get(0);

        mProxiedVirtualDisplayActivity.runOnUiThread(() -> buttonNode.performAction(
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS));

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(), TIMEOUT_MS,
                "WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED received");

        WindowCreationUtils.removeWindow(sUiAutomation, sInstrumentation,
                mProxiedVirtualDisplayActivity, topWindowView);
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_serviceAndProxySetA11yFocus_serviceAndProxyGetSeparateFocus()
            throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyAndEnableTouchExploration();
            // TODO(268752827): Investigate why the proxy window is invisible to to accessibility
            //  services nce the activity on the default display is launched. (Launching the default
            //  display activity will cause the windows of the virtual display to be cleared from
            // A11yWindowManager.)

            final EditText proxyEditText =
                    mProxiedVirtualDisplayActivity.findViewById(R.id.edit_text);
            setAccessibilityFocus(proxyEditText);

            final AccessibilityNodeInfo proxyA11yFocusedNode =
                    mA11yProxy.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
            assertThat(proxyA11yFocusedNode).isEqualTo(proxyEditText.createAccessibilityNodeInfo());

            // Launch an activity on the default display.
            mNonProxiedConcurrentActivity = launchProxyConcurrentActivityOnDefaultDisplay(service);

            final EditText serviceEditText =
                    mNonProxiedConcurrentActivity.findViewById(R.id.editText);
            setAccessibilityFocus(serviceEditText);
            final AccessibilityNodeInfo a11yFocusedNode = service.findFocus(
                    AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

            assertThat(a11yFocusedNode).isEqualTo(serviceEditText.createAccessibilityNodeInfo());
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_proxySetsInputFocus_proxyGetsInputFocus() throws TimeoutException {
        registerProxyAndWaitForConnection();

        // Make sure that the proxy display is the top-focused display.
        setTopFocusedDisplayIfNeeded(mVirtualDisplayId, mProxiedVirtualDisplayActivity,
                mA11yProxy.getWindows());

        final EditText editText = mProxiedVirtualDisplayActivity.findViewById(R.id.edit_text);
        setInputFocusIfNeeded(editText);

        final AccessibilityNodeInfo inputFocus = mA11yProxy.findFocus(
                AccessibilityNodeInfo.FOCUS_INPUT);
        assertThat(inputFocus).isEqualTo(editText.createAccessibilityNodeInfo());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#findFocus(int)"})
    public void testGetFocus_serviceSetsInputFocus_proxyDoesNotGetServiceInputFocus()
            throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            // Launch an activity on the default display.
            mNonProxiedConcurrentActivity = launchProxyConcurrentActivityOnDefaultDisplay(service);
            // Make sure that the default display is the top-focused display.
            setTopFocusedDisplayIfNeeded(Display.DEFAULT_DISPLAY, mNonProxiedConcurrentActivity,
                    service.getWindows());

            final EditText editText = mNonProxiedConcurrentActivity.findViewById(R.id.editText);
            setInputFocusIfNeeded(editText);

            final AccessibilityNodeInfo inputFocus = service.findFocus(
                    AccessibilityNodeInfo.FOCUS_INPUT);
            assertThat(inputFocus).isEqualTo(editText.createAccessibilityNodeInfo());
            assertThat(mA11yProxy.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)).isNull();
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testOnA11yEvent_touchProxyDisplay_proxyDoesNotReceiveInteractionEvent() {
        registerProxyAndWaitForConnection();
        mA11yProxy.setEventFilter(filterForEventType(
                AccessibilityEvent.TYPE_TOUCH_INTERACTION_START));

        // Try to trigger touch exploration, but fail.
        final MotionEvent downEvent = getDownMotionEvent(mProxiedVirtualDisplayActivityTitle,
                mA11yProxy.getWindows(), mVirtualDisplayId);
        sUiAutomation.injectInputEventToInputFilter(downEvent);

        assertThrows("The TYPE_TOUCH_INTERACTION_START event was received for a display"
                + " with disabled input.", AssertionError.class, () ->
                waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(),
                        TIMEOUT_MS, "(expected to timeout)"));
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onAccessibilityEvent"})
    public void testOnA11yEvent_touchDefaultDisplay_serviceReceivesInteractionEvent()
            throws Exception {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            // Launch an activity on the default display.
            mNonProxiedConcurrentActivity = launchProxyConcurrentActivityOnDefaultDisplay(service);
            service.setEventFilter(filterForEventType(
                    AccessibilityEvent.TYPE_TOUCH_INTERACTION_START));

            // Trigger touch exploration.
            final MotionEvent downEvent = getDownMotionEvent(getActivityTitle(sInstrumentation,
                            mNonProxiedConcurrentActivity), service.getWindows(),
                    mNonProxiedConcurrentActivity.getDisplayId());
            sUiAutomation.injectInputEventToInputFilter(downEvent);

            waitOn(service.mWaitObject, ()-> service.mReceivedEvent.get(), TIMEOUT_MS,
                    "Expected event was not received within " + TIMEOUT_MS + " ms");
        } finally {
            service.disableSelfAndRemove();
        }
    }

    private static MotionEvent getDownMotionEvent(CharSequence activityTitle,
            List<AccessibilityWindowInfo> windows, int displayId) {
        final Rect areaOfActivityWindowOnDisplay = new Rect();
        final AccessibilityWindowInfo window = findWindowByTitleWithList(activityTitle, windows);
        // Validity check: activity window exists.
        assertThat(window).isNotNull();

        window.getBoundsInScreen(areaOfActivityWindowOnDisplay);
        final int xOnScreen =
                areaOfActivityWindowOnDisplay.centerX();
        final int yOnScreen =
                areaOfActivityWindowOnDisplay.centerY();
        final long downEventTime = SystemClock.uptimeMillis();
        final MotionEvent downEvent = MotionEvent.obtain(downEventTime,
                downEventTime, MotionEvent.ACTION_DOWN, xOnScreen, yOnScreen, 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        downEvent.setDisplayId(displayId);
        return downEvent;
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".AccessibilityServicesStateChangeListener#onAccessibilityServicesStateChanged"})
    public void onAccessibilityServicesStateChanged_registerProxy_notifiesProxiedApp() {
        // Test that the proxy activity is notified that the services state is changed after
        // proxy registration.
        registerProxyWithTestServiceInfoAndWaitForServicesStateChange();
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".AccessibilityServicesStateChangeListener#onAccessibilityServicesStateChanged"})
    public void onAccessibilityServicesStateChanged_updateProxyEnabledList_notifiesProxiedApp() {
        // Test that the proxy activity is notified that the services state is changed after
        // a proxy update of installed and enabled services.
        registerProxyAndWaitForConnection();
        final MyAccessibilityServicesStateChangeListener listener =
                new MyAccessibilityServicesStateChangeListener(INTERACTIVE_UI_TIMEOUT,
                        NON_INTERACTIVE_UI_TIMEOUT);
        mProxyActivityA11yManager.addAccessibilityServicesStateChangeListener(listener);
        try {
            mA11yProxy.setInstalledAndEnabledServices(getTestAccessibilityServiceInfoAsList());
            waitOn(listener.mWaitObject, () -> listener.mAtomicBoolean.get(), TIMEOUT_MS,
                    "Services state listener should be called when proxy installed and"
                            + "enabled services are updated");
            assertTestAccessibilityServiceInfo(
                    mProxyActivityA11yManager.getInstalledAccessibilityServiceList());
            assertTestAccessibilityServiceInfo(
                    mProxyActivityA11yManager.getEnabledAccessibilityServiceList(
                            FEEDBACK_ALL_MASK));
        } finally {
            mProxyActivityA11yManager.removeAccessibilityServicesStateChangeListener(listener);
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".TouchExplorationStateChangeListener#onTouchExplorationStateChanged"})
    public void onTouchExplorationStateChanged_enableProxyTouchExploration_notifiesProxiedApp() {
        // Test that the proxy activity is notified that touch exploration is enabled after
        // proxy registration.
        registerProxyAndEnableTouchExploration();
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".AccessibilityServicesStateChangeListener#onAccessibilityServicesStateChanged"})
    public void testOnA11yServicesStateChanged_registerUnregisterProxy_resetsProxiedAppState() {
        // Tests that if a proxy is unregistered, the proxy app is updated to the non-proxy service
        // state.
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            // Set up the service info with timeouts.
            final AccessibilityServiceInfo nonProxyInfo = service.getServiceInfo();
            nonProxyInfo.setInteractiveUiTimeoutMillis(NON_PROXY_SERVICE_TIMEOUT);
            nonProxyInfo.setNonInteractiveUiTimeoutMillis(NON_PROXY_SERVICE_TIMEOUT);
            service.setServiceInfo(nonProxyInfo);
            // Register a proxy with different timeouts and make sure the activity is updated.
            registerProxyWithTestServiceInfoAndWaitForServicesStateChange();

            // When the proxy is unregistered, the a11y manager should be updated with the
            // StubProxyConcurrentAccessibilityService's timeouts.
            final MyAccessibilityServicesStateChangeListener listener =
                    new MyAccessibilityServicesStateChangeListener(NON_PROXY_SERVICE_TIMEOUT,
                            NON_PROXY_SERVICE_TIMEOUT);
            mProxyActivityA11yManager.addAccessibilityServicesStateChangeListener(listener);
            try {
                runWithShellPermissionIdentity(sUiAutomation, () ->
                        mA11yManager.unregisterDisplayProxy(mA11yProxy));
                waitOn(listener.mWaitObject, () -> listener.mAtomicBoolean.get(), TIMEOUT_MS,
                        "Services state change listener should be called when proxy is"
                                + " unregistered");
                // The service infos should no longer reflect the proxy and instead reflect
                // StubProxyConcurrentAccessibilityService.
                final List<AccessibilityServiceInfo> installedServices =
                        mProxyActivityA11yManager.getInstalledAccessibilityServiceList();
                final List<AccessibilityServiceInfo> enabledServices =
                        mProxyActivityA11yManager.getEnabledAccessibilityServiceList(
                                FEEDBACK_ALL_MASK);
                boolean installedServicesHasConcurrentService = false;
                for (AccessibilityServiceInfo installedInfo : installedServices) {
                    if (installedInfo.equals(nonProxyInfo)) {
                        installedServicesHasConcurrentService = true;
                        break;
                    }
                }
                assertThat(installedServicesHasConcurrentService).isTrue();
                assertThat(enabledServices.get(0)).isEqualTo(nonProxyInfo);
            } finally {
                mProxyActivityA11yManager.removeAccessibilityServicesStateChangeListener(listener);
            }
        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".TouchExplorationStateChangeListener#onTouchExplorationStateChanged"})
    public void testOnTouchExplorationStateChanged_registerUnregisterProxy_resetsProxiedAppState() {
        registerProxyAndEnableTouchExploration();
        final MyTouchExplorationStateChangeListener listener =
                new MyTouchExplorationStateChangeListener(/* initialState */ true);
        mProxyActivityA11yManager.addTouchExplorationStateChangeListener(listener);
        try {
            // Test that touch exploration is disabled for the app when the proxy is unregistered.
            runWithShellPermissionIdentity(sUiAutomation, () ->
                    mA11yManager.unregisterDisplayProxy(mA11yProxy));
            waitOn(listener.mWaitObject, () -> !listener.mAtomicBoolean.get(), TIMEOUT_MS,
                    "The touch exploration listener should be notified when the proxy is"
                            + " unregistered");
            assertThat(mProxyActivityA11yManager.isTouchExplorationEnabled()).isFalse();
        } finally {
            mProxyActivityA11yManager.removeTouchExplorationStateChangeListener(listener);
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".AccessibilityServicesStateChangeListener#onAccessibilityServicesStateChanged"})
    public void testOnA11yServicesStateChanged_updateServiceTimeout_doesNotNotifyProxiedApp() {
        // Note: A service has to be enabled before registering a proxy, otherwise the installed
        // list won't contain this service and enableService() fails. This is because the
        // instrumentation process would be associated with the proxy device id and the proxy's
        // services (A11yManager#getInstalledAccessibilityServiceList would belong to the proxy).
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyWithTestServiceInfoAndWaitForServicesStateChange();
            final MyAccessibilityServicesStateChangeListener listener =
                    new MyAccessibilityServicesStateChangeListener(NON_PROXY_SERVICE_TIMEOUT,
                            NON_PROXY_SERVICE_TIMEOUT);
            mProxyActivityA11yManager.addAccessibilityServicesStateChangeListener(listener);
            try {
                final AccessibilityServiceInfo nonProxyInfo = service.getServiceInfo();
                nonProxyInfo.setInteractiveUiTimeoutMillis(NON_PROXY_SERVICE_TIMEOUT);
                nonProxyInfo.setNonInteractiveUiTimeoutMillis(NON_PROXY_SERVICE_TIMEOUT);
                service.setServiceInfo(nonProxyInfo);

                assertThrows("The A11yManager of the proxy-ed app was notified of a"
                                + " non-proxy service change.", AssertionError.class,
                        () -> waitOn(listener.mWaitObject, () -> listener.mAtomicBoolean.get(),
                                TIMEOUT_MS, "(expected to timeout)"));
                // Verify the activity has the proxy timeouts.
                assertThat(mProxyActivityA11yManager.getRecommendedTimeoutMillis(
                        /* originalTimeout */0,
                        FLAG_CONTENT_CONTROLS)).isEqualTo(INTERACTIVE_UI_TIMEOUT);
                assertThat(mProxyActivityA11yManager.getRecommendedTimeoutMillis(
                        /* originalTimeout */ 0,
                        FLAG_CONTENT_TEXT)).isEqualTo(NON_INTERACTIVE_UI_TIMEOUT);
            } finally {
                mProxyActivityA11yManager.removeAccessibilityServicesStateChangeListener(listener);
            }

        } finally {
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".TouchExplorationStateChangeListener#onTouchExplorationStateChanged"})
    public void testOnTouchExplorationChanged_updateServiceTouchState_doesNotNotifyProxiedApp() {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            registerProxyAndWaitForConnection();
            final MyTouchExplorationStateChangeListener listener =
                    new MyTouchExplorationStateChangeListener(/* initialState */ false);
            mProxyActivityA11yManager.addTouchExplorationStateChangeListener(listener);
            try {
                final AccessibilityServiceInfo info = service.getServiceInfo();
                info.flags &=
                        ~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
                service.setServiceInfo(info);
                assertThrows("A proxy-ed app with touch exploration enabled was notified"
                                + " of a non-proxy service disabling touch exploration.",
                        AssertionError.class, () ->
                        waitOn(listener.mWaitObject, () -> listener.mAtomicBoolean.get(),
                                TIMEOUT_MS, "(expected to timeout)"));
            } finally {
                mProxyActivityA11yManager.removeTouchExplorationStateChangeListener(listener);
            }
        } finally {
            // Reset touch exploration for the non-proxy service.
            final AccessibilityServiceInfo info = service.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            service.setServiceInfo(info);
            service.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".AccessibilityServicesStateChangeListener#onAccessibilityServicesStateChanged"})
    public void testOnA11yServicesStateChanged_enableService_notifiesNonProxiedApp()
            throws TimeoutException {
        // Verify that enabling a non-proxy service will update the non-proxy AccessibilityManager.
        // On the service state change, the activity will emit a TYPE_ANNOUNCEMENT event with its
        // enabled services. Use TYPE_ANNOUNCEMENT instead of window events since enableService()
        // will indirectly disconnect the UI automation and reset its
        // FLAG_RETRIEVE_INTERACTIVE_WINDOWS.
        final AtomicReference<StubProxyConcurrentAccessibilityService> service =
                new AtomicReference<>();
        AtomicReference<CharSequence> serviceName = new AtomicReference<>();
        try {
            startActivityInSeparateProcess();
            sUiAutomation.executeAndWaitForEvent(() ->
                            service.set(mNonProxyServiceRule.enableService()),
                    (event -> {
                        if (event.getEventType() == TYPE_ANNOUNCEMENT) {
                            final CharSequence name = service.get()
                                    .getServiceInfo().getResolveInfo().serviceInfo.name;
                            serviceName.set(name);
                            return eventTextHasText(event.getText(), name);
                        }
                        return false;
                    }), TIMEOUT_MS);
            assertThat(service.get()).isNotNull();
            assertThat(serviceName.get()).isNotNull();
            sUiAutomation.executeAndWaitForEvent(() -> service.get().disableSelfAndRemove(),
                    (event -> event.getEventType() == TYPE_ANNOUNCEMENT
                            && !eventTextHasText(event.getText(), serviceName.get())), TIMEOUT_MS);
        } finally {
            if (service.get() != null) {
                service.get().disableSelfAndRemove();
            }
            stopSeparateProcess();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".TouchExplorationStateChangeListener#onTouchExplorationStateChanged"})
    public void testOnTouchExplorationStateChanged_updateServiceTouchState_notifiesNonProxiedApp()
            throws TimeoutException {
        // Verify that enabling and disabling touch exploration for a non-proxy a11y service will
        // update the non-proxy AccessibilityManager.
        // On touch exploration state change, the activity will emit a TYPE_ANNOUNCEMENT event with
        // its touch exploration state. Use TYPE_ANNOUNCEMENT instead of window events since
        // enableService() will indirectly disconnect the UI automation and reset its
        // FLAG_RETRIEVE_INTERACTIVE_WINDOWS.
        final AtomicReference<StubProxyConcurrentAccessibilityService> service =
                new AtomicReference<>();
        try {
            startActivityInSeparateProcess();
            sUiAutomation.executeAndWaitForEvent(() ->
                            service.set(mNonProxyServiceRule.enableService()),
                    (event -> event.getEventType() == TYPE_ANNOUNCEMENT
                            && eventTextHasText(event.getText(),
                            TOUCH_EXPLORATION_CHANGE_EVENT_TEXT + true)) , TIMEOUT_MS);
            sUiAutomation.executeAndWaitForEvent(
                    () -> {
                        InstrumentedAccessibilityService a11yService = service.get();
                        final AccessibilityServiceInfo info = a11yService.getServiceInfo();
                        info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
                        a11yService.setServiceInfo(info);
                    },
                    (event -> event.getEventType() == TYPE_ANNOUNCEMENT
                    && eventTextHasText(event.getText(),
                    TOUCH_EXPLORATION_CHANGE_EVENT_TEXT + false)) , TIMEOUT_MS);
        } finally {
            if (service.get() != null) {
                // Reset touch exploration for the non-proxy service.
                final AccessibilityServiceInfo info = service.get().getServiceInfo();
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
                service.get().setServiceInfo(info);
                service.get().disableSelfAndRemove();
            }
            stopSeparateProcess();
        }
    }
    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".AccessibilityServicesStateChangeListener#onAccessibilityServicesStateChanged"})
    public void testOnA11yServicesStateChanged_registerProxy_doesNotNotifyNonProxiedApp()
            throws TimeoutException {
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            startActivityInSeparateProcess();
            assertThrows("A non-proxy-ed app was notified of a proxy change and sent out an"
                            + " event.",
                    TimeoutException.class,
                    () -> sUiAutomation.executeAndWaitForEvent(
                        () -> registerProxyAndWaitForConnection(),
                        (event -> {
                            final CharSequence name =
                                    mA11yProxy.getInstalledAndEnabledServices().get(0)
                                            .getResolveInfo().serviceInfo.name;
                            return event.getEventType() == TYPE_ANNOUNCEMENT
                                    && eventTextHasText(event.getText(), name);
                        }), TIMEOUT_MS));
        } finally {
            service.disableSelfAndRemove();
            stopSeparateProcess();
        }
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityManager"
            + ".TouchExplorationStateChangeListener#onTouchExplorationStateChanged"})
    public void testOnTouchExplorationStateChanged_registerProxy_doesNotNotifyNonProxiedApp()
            throws TimeoutException {
        // A service has touch exploration enabled by default.
        final StubProxyConcurrentAccessibilityService service =
                mNonProxyServiceRule.enableService();
        try {
            assertThat((service.getServiceInfo().flags
                    & AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0).isTrue();
            startActivityInSeparateProcess();
            assertThrows("The non-proxy-ed app with touch exploration enabled was "
                            + "notified of a proxy that does not have touch exploration enabled.",
                    TimeoutException.class, () -> sUiAutomation.executeAndWaitForEvent(
                            () -> registerProxyAndWaitForConnection(),
                            (event -> event.getEventType() == TYPE_ANNOUNCEMENT
                                    && eventTextHasText(event.getText(),
                                    TOUCH_EXPLORATION_CHANGE_EVENT_TEXT + false)) , TIMEOUT_MS));
        } finally {
            service.disableSelfAndRemove();
            stopSeparateProcess();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityDisplayProxy#setInstalledAndEnabledServices",
            "android.view.accessibility.AccessibilityDisplayProxy#getInstalledAndEnabledServices"})
    public void testGetInstalledAndEnabledAccessibilityServices_proxySetsList_getsList() {
        mA11yProxy = new MyA11yProxy(mVirtualDisplayId, Executors.newSingleThreadExecutor(),
                new ArrayList<>());
        registerProxyAndWaitForConnection();

        mA11yProxy.setInstalledAndEnabledServices(getTestAccessibilityServiceInfoAsList());

        assertTestAccessibilityServiceInfo(mA11yProxy.getInstalledAndEnabledServices());
    }

    @Test
    @ApiTest(apis = {
            "android.view.accessibility.AccessibilityDisplayProxy#getInstalledAndEnabledServices"})
    public void testGetInstalledAndEnabledAccessibilityServices_registerProxyWithList_getsList() {
        mA11yProxy = new MyA11yProxy(mVirtualDisplayId, Executors.newSingleThreadExecutor(),
                getTestAccessibilityServiceInfoAsList());
        registerProxyAndWaitForConnection();

        assertTestAccessibilityServiceInfo(mA11yProxy.getInstalledAndEnabledServices());
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#interrupt"})
    public void testInterrupt_always_causesInterrupt() {
        registerProxyAndWaitForConnection();

        mProxyActivityA11yManager.interrupt();

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mInterrupted.get(), TIMEOUT_MS,
                "Proxy interrupted");
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#onProxyConnected"})
    public void testOnProxyConnected_always_connectsProxy() {
        registerProxyAndWaitForConnection();
    }

    private void registerProxyAndWaitForConnection() {
        runWithShellPermissionIdentity(sUiAutomation,
                () -> mA11yManager.registerDisplayProxy(mA11yProxy));

        waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mConnected.get(), TIMEOUT_MS,
                "Proxy connected");
    }

    @Test
    @ApiTest(apis = {"android.view.accessibility.AccessibilityDisplayProxy#setFocusAppearance"})
    public void testSetFocusAppearance_always_updatesAppWithAppearance() {
        registerProxyAndEnableTouchExploration();
        // This test verifies that the proxy can set the user's focus appearance, which affects all
        // apps. Ideally this should only affect the apps that are proxy-ed.
        // TODO(264594384): Test that a non-proxy activity does not get a changed focus appearance.
        final int width = mProxyActivityA11yManager.getAccessibilityFocusStrokeWidth();
        final int color = mProxyActivityA11yManager.getAccessibilityFocusColor();
        final int updatedWidth = width + 10;
        final int updatedColor = color == Color.BLUE ? Color.RED : Color.BLUE;

        try {
            setFocusAppearanceDataAndCheckItCorrect(mA11yProxy, updatedWidth, updatedColor);
        } finally {
            setFocusAppearanceDataAndCheckItCorrect(mA11yProxy, width, color);
        }
    }

    private void setFocusAppearanceDataAndCheckItCorrect(AccessibilityDisplayProxy proxy,
            int focusStrokeWidthValue, int focusColorValue) {
        proxy.setAccessibilityFocusAppearance(focusStrokeWidthValue,
                focusColorValue);
        // Checks if the color and the stroke values from AccessibilityManager are
        // updated as expected.
        PollingCheck.waitFor(()->
                mProxyActivityA11yManager.getAccessibilityFocusStrokeWidth()
                        == focusStrokeWidthValue
                        && mProxyActivityA11yManager.getAccessibilityFocusColor()
                        == focusColorValue);
    }

    private void registerProxyWithTestServiceInfoAndWaitForServicesStateChange() {
        mA11yProxy = new MyA11yProxy(mVirtualDisplayId, Executors.newSingleThreadExecutor(),
                getTestAccessibilityServiceInfoAsList());

        // Test that the A11yManager is notified when the timeouts have successfully propagated.
        final MyAccessibilityServicesStateChangeListener listener =
                new MyAccessibilityServicesStateChangeListener(INTERACTIVE_UI_TIMEOUT,
                        NON_INTERACTIVE_UI_TIMEOUT);
        mProxyActivityA11yManager.addAccessibilityServicesStateChangeListener(listener);
        try {
            runWithShellPermissionIdentity(sUiAutomation,
                    () -> mA11yManager.registerDisplayProxy(mA11yProxy));
            waitOn(listener.mWaitObject, () -> listener.mAtomicBoolean.get(), TIMEOUT_MS,
                    "Proxy AccessibilityServicesStateChangeListener called when proxy"
                            + " is registered");
            // Make sure the installed and enabled services are correct.
            assertTestAccessibilityServiceInfo(
                    mProxyActivityA11yManager.getInstalledAccessibilityServiceList());
            assertTestAccessibilityServiceInfo(
                    mProxyActivityA11yManager.getEnabledAccessibilityServiceList(
                            FEEDBACK_ALL_MASK));
        } finally {
            mProxyActivityA11yManager.removeAccessibilityServicesStateChangeListener(listener);
        }
    }

    private AccessibilityEvent getProxyClickAccessibilityEvent() {
        final AccessibilityEvent clickEvent = new AccessibilityEvent();
        clickEvent.setEventType(AccessibilityEvent.TYPE_VIEW_CLICKED);
        clickEvent.setClassName(Button.class.getName());
        clickEvent.setDisplayId(mA11yProxy.getDisplayId());
        clickEvent.getText().add(mProxiedVirtualDisplayActivity.getString(R.string.button_title));
        return clickEvent;
    }

    private UiAutomation.AccessibilityEventFilter getClickEventFilter(
            AccessibilityEvent clickEvent) {
        return allOf(
                new AccessibilityEventFilterUtils.AccessibilityEventTypeMatcher(TYPE_VIEW_CLICKED),
                AccessibilityEventFilterUtils.matcherForDisplayId(clickEvent.getDisplayId()),
                AccessibilityEventFilterUtils.matcherForClassName(clickEvent.getClassName()),
                AccessibilityEventFilterUtils.matcherForFirstText(clickEvent.getText().get(0)))
                ::matches;
    }

    private List<AccessibilityServiceInfo> getTestAccessibilityServiceInfoAsList() {
        final List<AccessibilityServiceInfo> infos = new ArrayList<>();
        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.setAccessibilityTool(true);
        info.packageNames = new String[]{PACKAGE_1, PACKAGE_2};
        info.setInteractiveUiTimeoutMillis(INTERACTIVE_UI_TIMEOUT);
        info.setNonInteractiveUiTimeoutMillis(NON_INTERACTIVE_UI_TIMEOUT);
        info.notificationTimeout = NOTIFICATION_TIMEOUT;
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = FEEDBACK_AUDIBLE;
        info.flags = FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        infos.add(info);
        return infos;
    }

    private void assertTestAccessibilityServiceInfo(List<AccessibilityServiceInfo> infos) {
        assertThat(infos.size()).isEqualTo(1);
        final AccessibilityServiceInfo retrievedInfo = infos.get(0);
        // Assert individual properties, since info.equals checks the ComponentName, which is
        // populated in the info instances belonging to the system process but not in those held by
        // the proxy.
        assertThat(retrievedInfo.isAccessibilityTool()).isTrue();
        assertThat(retrievedInfo.packageNames).isEqualTo(new String[]{PACKAGE_1, PACKAGE_2});
        assertThat(retrievedInfo.getInteractiveUiTimeoutMillis()).isEqualTo(
                INTERACTIVE_UI_TIMEOUT);
        assertThat(retrievedInfo.getNonInteractiveUiTimeoutMillis()).isEqualTo(
                NON_INTERACTIVE_UI_TIMEOUT);
        assertThat(retrievedInfo.notificationTimeout).isEqualTo(NOTIFICATION_TIMEOUT);
        assertThat(retrievedInfo.eventTypes).isEqualTo(AccessibilityEvent.TYPE_VIEW_CLICKED);
        assertThat(retrievedInfo.feedbackType).isEqualTo(FEEDBACK_AUDIBLE);
        assertThat(retrievedInfo.flags).isEqualTo(FLAG_INCLUDE_NOT_IMPORTANT_VIEWS);
    }

    private void setAccessibilityFocus(View view) throws TimeoutException {
        if (!view.isAccessibilityFocused()) {
            sUiAutomation.executeAndWaitForEvent(
                    () -> mProxiedVirtualDisplayActivity.runOnUiThread(
                            () -> view.performAccessibilityAction(
                                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)),
                    filterForEventType(TYPE_VIEW_ACCESSIBILITY_FOCUSED), TIMEOUT_MS);
        }
    }

    private void setInitialAccessibilityFocusAndWaitForProxyEvents(EditText proxyEditText) {
        mA11yProxy.setEventFilter(filterWaitForAll(
                filterForEventType(TYPE_VIEW_ACCESSIBILITY_FOCUSED),
                filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED)));
        if (!proxyEditText.isAccessibilityFocused()) {
            proxyEditText.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
            waitOn(mA11yProxy.mWaitObject, ()-> mA11yProxy.mReceivedEvent.get(), TIMEOUT_MS,
                    "Expected event was not received within " + TIMEOUT_MS + " ms");
        }

        final AccessibilityNodeInfo a11yFocusedNode = mA11yProxy.findFocus(
                AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        assertThat(a11yFocusedNode).isEqualTo(proxyEditText.createAccessibilityNodeInfo());

    }

    private void setInputFocusIfNeeded(View view) throws TimeoutException {
        if (!view.isFocused()) {
            sUiAutomation.executeAndWaitForEvent(
                    () -> mProxiedVirtualDisplayActivity.runOnUiThread(() -> {
                        // Ensure state for taking input focus.
                        view.setVisibility(View.VISIBLE);
                        view.setFocusable(true);
                        view.performAccessibilityAction(
                                AccessibilityNodeInfo.ACTION_FOCUS, null);
                    }) ,
                    filterForEventType(TYPE_VIEW_FOCUSED), TIMEOUT_MS);
        }
    }

    private void setTopFocusedDisplayIfNeeded(int displayId, Activity activity,
            List<AccessibilityWindowInfo> windows) throws TimeoutException {
        boolean isTopFocusedDisplay = false;
        for (AccessibilityWindowInfo window : windows) {
            // A focused window of the display means this display is the top-focused display.
            if (window.isFocused()) {
                isTopFocusedDisplay = true;
                break;
            }
        }
        if (!isTopFocusedDisplay) {
            sUiAutomation.executeAndWaitForEvent(
                    () -> DisplayUtils.touchDisplay(sUiAutomation, displayId, activity.getTitle()),
                    filterWindowsChangedWithChangeTypes(WINDOWS_CHANGE_FOCUSED
                            | WINDOWS_CHANGE_ACTIVE), TIMEOUT_MS);
        }
    }

    private void assertVirtualDisplayActivityExistsToProxy() {
        final List<AccessibilityWindowInfo> proxyWindows = mA11yProxy.getWindows();
        assertThat(findWindowByTitleWithList(
                mProxiedVirtualDisplayActivityTitle, proxyWindows)).isNotNull();
    }

    private NonProxyActivity launchProxyConcurrentActivityOnDefaultDisplay(
            InstrumentedAccessibilityService service)
            throws Exception {
        final NonProxyActivity nonProxyActivity =
                launchActivityAndWaitForItToBeOnscreen(
                        sInstrumentation, sUiAutomation,
                        mNonProxyActivityRule);
        final List<AccessibilityWindowInfo> serviceWindows = service.getWindows();
        assertThat(findWindowByTitleWithList(getActivityTitle(sInstrumentation,
                nonProxyActivity), serviceWindows)).isNotNull();
        return nonProxyActivity;
    }

    private View showTopWindowAndWaitForItToShowUp() throws TimeoutException {
        final WindowManager.LayoutParams paramsForTop =
                WindowCreationUtils.layoutParamsForWindowOnTop(
                        sInstrumentation, mProxiedVirtualDisplayActivity, TOP_WINDOW_TITLE);
        final Button button = new Button(mProxiedVirtualDisplayActivity);
        button.setText(sInstrumentation.getContext().getString(R.string.button1));
        WindowCreationUtils.addWindowAndWaitForEvent(sUiAutomation, sInstrumentation,
                mProxiedVirtualDisplayActivity,
                button, paramsForTop, (event) -> (event.getEventType() == TYPE_WINDOWS_CHANGED)
                        && (findWindowByTitleWithList(mProxiedVirtualDisplayActivityTitle,
                        mA11yProxy.getWindows())
                        != null)
                        && (findWindowByTitleWithList(TOP_WINDOW_TITLE, mA11yProxy.getWindows())
                        != null));
        return button;
    }

    private VirtualDisplay createVirtualDeviceAndLaunchVirtualDisplay() {
        sUiAutomation.adoptShellPermissionIdentity(ADD_TRUSTED_DISPLAY, CREATE_VIRTUAL_DEVICE);
        VirtualDisplay display;
        mVirtualDevice = mVirtualDeviceManager.createVirtualDevice(
                        mFakeAssociationRule.getAssociationInfo().getId(),
                        DEFAULT_VIRTUAL_DEVICE_PARAMS);
        // Values taken from StreamedAppClipboardTest
        mImageReader = ImageReader.newInstance(/* width= */ 100, /* height= */ 100,
                PixelFormat.RGBA_8888, /* maxImages= */ 1);
        display = mVirtualDevice.createVirtualDisplay(
                /* width= */ mImageReader.getWidth(),
                /* height= */ mImageReader.getHeight(),
                /* densityDpi= */ 240,
                mImageReader.getSurface(),
                0,
                Runnable::run,
                new VirtualDisplay.Callback(){});
        return display;
    }

    private Activity launchActivityOnVirtualDisplay(int virtualDisplayId) throws Exception {
        final Activity activityOnVirtualDisplay =
                launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(sInstrumentation,
                        sUiAutomation,
                        ProxyDisplayActivity.class,
                        virtualDisplayId);
        return activityOnVirtualDisplay;
    }

    private void startActivityInSeparateProcess() throws TimeoutException {
        final AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);

        // Specify the default display, else this may get launched on the virtual display.
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);

        sUiAutomation.executeAndWaitForEvent(() ->
                        sInstrumentation.getContext().startActivity(
                                mSeparateProcessActivityIntent, options.toBundle()),
                AccessibilityEventFilterUtils.filterWindowsChangeTypesAndWindowTitle(sUiAutomation,
                        WINDOWS_CHANGE_ADDED, SEPARATE_PROCESS_ACTIVITY_TITLE), TIMEOUT_MS);
    }

    private void stopSeparateProcess() {
        // Make sure to kill off the separate process.
        final List<String> allPackageNames = new ArrayList<>();
        allPackageNames.add(SEPARATE_PROCESS_PACKAGE_NAME);
        final ActivityManager am =
                sInstrumentation.getContext().getSystemService(ActivityManager.class);
        for (final String pkgName : allPackageNames) {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                am.forceStopPackage(pkgName);
            });
        }
    }

    private boolean eventTextHasText(List<CharSequence> text, CharSequence name) {
        if (text == null) {
            return false;
        }
        for (CharSequence textSegment : text) {
            if (textSegment.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void registerProxyAndEnableTouchExploration() {
        registerProxyAndWaitForConnection();
        assertVirtualDisplayActivityExistsToProxy();
        final MyTouchExplorationStateChangeListener listener =
                new MyTouchExplorationStateChangeListener(/* initialState */ false);
        mProxyActivityA11yManager.addTouchExplorationStateChangeListener(listener);
        try {
            final List<AccessibilityServiceInfo> serviceInfos =
                    mA11yProxy.getInstalledAndEnabledServices();
            serviceInfos.get(0).flags |=
                    AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            mA11yProxy.setInstalledAndEnabledServices(serviceInfos);
            waitOn(listener.mWaitObject, () -> listener.mAtomicBoolean.get(), TIMEOUT_MS,
                    "Touch exploration state listener called");
            assertThat(mProxyActivityA11yManager.isTouchExplorationEnabled()).isTrue();
        } finally {
            mProxyActivityA11yManager.removeTouchExplorationStateChangeListener(listener);
        }
    }

    class MyAccessibilityServicesStateChangeListener
            implements AccessibilityServicesStateChangeListener {
        AtomicBoolean mAtomicBoolean = new AtomicBoolean(false);
        Object mWaitObject = new Object();
        int mExpectedInteractiveTimeout;
        int mExpectedNonInteractiveTimeout;

        MyAccessibilityServicesStateChangeListener(int expectedInteractiveTimeout,
                int expectedNonInteractiveTimeout) {
            mExpectedInteractiveTimeout = expectedInteractiveTimeout;
            mExpectedNonInteractiveTimeout = expectedNonInteractiveTimeout;
        }

        @Override
        public void onAccessibilityServicesStateChanged(@NonNull AccessibilityManager manager) {
            final int recommendedInteractiveUiTimeout =
                    mProxyActivityA11yManager.getRecommendedTimeoutMillis(
                            0, FLAG_CONTENT_CONTROLS);
            final int recommendedNonInteractiveUiTimeout =
                    mProxyActivityA11yManager.getRecommendedTimeoutMillis(
                            0, FLAG_CONTENT_TEXT);
            final boolean updatedTimeouts =
                    recommendedInteractiveUiTimeout == mExpectedInteractiveTimeout
                            && recommendedNonInteractiveUiTimeout == mExpectedNonInteractiveTimeout;
            synchronized (mWaitObject) {
                mAtomicBoolean.set(updatedTimeouts);
                mWaitObject.notifyAll();
            }
        }
    }


    class MyTouchExplorationStateChangeListener implements
            TouchExplorationStateChangeListener {
        AtomicBoolean mAtomicBoolean;
        Object mWaitObject = new Object();

        MyTouchExplorationStateChangeListener(boolean initialState) {
            mAtomicBoolean = new AtomicBoolean(initialState);
        }
        @Override
        public void onTouchExplorationStateChanged(boolean enabled) {
            synchronized (mWaitObject) {
                mAtomicBoolean.set(enabled);
                mWaitObject.notifyAll();
            }
        }
    }

    /**
     * Class for testing AccessibilityDisplayProxy.
     */
    class MyA11yProxy extends AccessibilityDisplayProxy {
        AtomicBoolean mReceivedEvent = new AtomicBoolean();
        AtomicBoolean mConnected = new AtomicBoolean();
        AtomicBoolean mInterrupted = new AtomicBoolean();
        UiAutomation.AccessibilityEventFilter mEventFilter;
        final Object mWaitObject = new Object();
        MyA11yProxy(int displayId, Executor executor, List<AccessibilityServiceInfo> infos) {
            super(displayId, executor, infos);
        }

        @Override
        public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {
            // TODO(b/276745079): Collect failing events.
            if (mEventFilter != null) {
                if (mEventFilter.accept(event)) {
                    synchronized (mWaitObject) {
                        mReceivedEvent.set(true);
                        mWaitObject.notifyAll();
                    }
                }
            }
        }

        @Override
        public void onProxyConnected() {
            synchronized (mWaitObject) {
                mConnected.set(true);
                mWaitObject.notifyAll();
            }
        }

        @Override
        public void interrupt() {
            synchronized (mWaitObject) {
                mInterrupted.set(true);
                mWaitObject.notifyAll();
            }
        }

        public void setEventFilter(@NonNull UiAutomation.AccessibilityEventFilter filter) {
            mReceivedEvent.set(false);
            mEventFilter = filter;
        }
    }
}
