/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.compat.sjp.cts;

import static android.compat.testing.Classpaths.ClasspathType.BOOTCLASSPATH;
import static android.compat.testing.Classpaths.ClasspathType.SYSTEMSERVERCLASSPATH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.compat.testing.Classpaths;
import android.compat.testing.SharedLibraryInfo;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * Tests for detecting no duplicate class files are present on BOOTCLASSPATH and
 * SYSTEMSERVERCLASSPATH.
 *
 * <p>Duplicate class files are not safe as some of the jars on *CLASSPATH are updated outside of
 * the main dessert release cycle; they also contribute to unnecessary disk space usage.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class StrictJavaPackagesTest extends BaseHostJUnit4Test {

    /**
     * This is the list of classes that are currently duplicated and should be addressed.
     *
     * <p> DO NOT ADD CLASSES TO THIS LIST!
     */
    private static final Set<String> BCP_AND_SSCP_OVERLAP_BURNDOWN_LIST =
            ImmutableSet.of(
                    "Landroid/annotation/AnimatorRes;",
                    "Landroid/annotation/AnimRes;",
                    "Landroid/annotation/AnyRes;",
                    "Landroid/annotation/AnyThread;",
                    "Landroid/annotation/AppIdInt;",
                    "Landroid/annotation/ArrayRes;",
                    "Landroid/annotation/AttrRes;",
                    "Landroid/annotation/BinderThread;",
                    "Landroid/annotation/BoolRes;",
                    "Landroid/annotation/BroadcastBehavior;",
                    "Landroid/annotation/BytesLong;",
                    "Landroid/annotation/CallbackExecutor;",
                    "Landroid/annotation/CallSuper;",
                    "Landroid/annotation/CheckResult;",
                    "Landroid/annotation/ColorInt;",
                    "Landroid/annotation/ColorLong;",
                    "Landroid/annotation/ColorRes;",
                    "Landroid/annotation/Condemned;",
                    "Landroid/annotation/CurrentTimeMillisLong;",
                    "Landroid/annotation/CurrentTimeSecondsLong;",
                    "Landroid/annotation/DimenRes;",
                    "Landroid/annotation/Dimension;",
                    "Landroid/annotation/Discouraged;",
                    "Landroid/annotation/DisplayContext;",
                    "Landroid/annotation/DrawableRes;",
                    "Landroid/annotation/DurationMillisLong;",
                    "Landroid/annotation/ElapsedRealtimeLong;",
                    "Landroid/annotation/FloatRange;",
                    "Landroid/annotation/FontRes;",
                    "Landroid/annotation/FractionRes;",
                    "Landroid/annotation/HalfFloat;",
                    "Landroid/annotation/Hide;",
                    "Landroid/annotation/IdRes;",
                    "Landroid/annotation/IntDef;",
                    "Landroid/annotation/IntegerRes;",
                    "Landroid/annotation/InterpolatorRes;",
                    "Landroid/annotation/IntRange;",
                    "Landroid/annotation/LayoutRes;",
                    "Landroid/annotation/LongDef;",
                    "Landroid/annotation/MainThread;",
                    "Landroid/annotation/MenuRes;",
                    "Landroid/annotation/NavigationRes;",
                    "Landroid/annotation/NonNull;",
                    "Landroid/annotation/NonUiContext;",
                    "Landroid/annotation/Nullable;",
                    "Landroid/annotation/PluralsRes;",
                    "Landroid/annotation/Px;",
                    "Landroid/annotation/RawRes;",
                    "Landroid/annotation/RequiresFeature;",
                    "Landroid/annotation/RequiresNoPermission;",
                    "Landroid/annotation/RequiresPermission;",
                    "Landroid/annotation/SdkConstant;",
                    "Landroid/annotation/Size;",
                    "Landroid/annotation/StringDef;",
                    "Landroid/annotation/StringRes;",
                    "Landroid/annotation/StyleableRes;",
                    "Landroid/annotation/StyleRes;",
                    "Landroid/annotation/SuppressAutoDoc;",
                    "Landroid/annotation/SuppressLint;",
                    "Landroid/annotation/SystemApi;",
                    "Landroid/annotation/SystemService;",
                    "Landroid/annotation/TargetApi;",
                    "Landroid/annotation/TestApi;",
                    "Landroid/annotation/TransitionRes;",
                    "Landroid/annotation/UiContext;",
                    "Landroid/annotation/UiThread;",
                    "Landroid/annotation/UptimeMillisLong;",
                    "Landroid/annotation/UserHandleAware;",
                    "Landroid/annotation/UserIdInt;",
                    "Landroid/annotation/Widget;",
                    "Landroid/annotation/WorkerThread;",
                    "Landroid/annotation/XmlRes;",
                    "Landroid/gsi/AvbPublicKey;",
                    "Landroid/gsi/GsiProgress;",
                    "Landroid/gsi/IGsiService;",
                    "Landroid/gsi/IGsiServiceCallback;",
                    "Landroid/gsi/IImageService;",
                    "Landroid/gsi/IProgressCallback;",
                    "Landroid/gsi/MappedImage;",
                    "Landroid/gui/TouchOcclusionMode;",
                    "Landroid/hardware/contexthub/V1_0/AsyncEventType;",
                    "Landroid/hardware/contexthub/V1_0/ContextHub;",
                    "Landroid/hardware/contexthub/V1_0/ContextHubMsg;",
                    "Landroid/hardware/contexthub/V1_0/HostEndPoint;",
                    "Landroid/hardware/contexthub/V1_0/HubAppInfo;",
                    "Landroid/hardware/contexthub/V1_0/HubMemoryFlag;",
                    "Landroid/hardware/contexthub/V1_0/HubMemoryType;",
                    "Landroid/hardware/contexthub/V1_0/IContexthub;",
                    "Landroid/hardware/contexthub/V1_0/IContexthubCallback;",
                    "Landroid/hardware/contexthub/V1_0/MemRange;",
                    "Landroid/hardware/contexthub/V1_0/NanoAppBinary;",
                    "Landroid/hardware/contexthub/V1_0/NanoAppFlags;",
                    "Landroid/hardware/contexthub/V1_0/PhysicalSensor;",
                    "Landroid/hardware/contexthub/V1_0/Result;",
                    "Landroid/hardware/contexthub/V1_0/SensorType;",
                    "Landroid/hardware/contexthub/V1_0/TransactionResult;",
                    "Landroid/hardware/usb/gadget/V1_0/GadgetFunction;",
                    "Landroid/hardware/usb/gadget/V1_0/IUsbGadget;",
                    "Landroid/hardware/usb/gadget/V1_0/IUsbGadgetCallback;",
                    "Landroid/hardware/usb/gadget/V1_0/Status;",
                    "Landroid/os/IDumpstate;",
                    "Landroid/os/IDumpstateListener;",
                    "Landroid/os/IInstalld;",
                    "Landroid/os/IStoraged;",
                    "Landroid/os/IVold;",
                    "Landroid/os/IVoldListener;",
                    "Landroid/os/IVoldMountCallback;",
                    "Landroid/os/IVoldTaskListener;",
                    "Landroid/os/storage/CrateMetadata;",
                    "Landroid/view/LayerMetadataKey;",
                    "Lcom/android/internal/annotations/CompositeRWLock;",
                    "Lcom/android/internal/annotations/GuardedBy;",
                    "Lcom/android/internal/annotations/Immutable;",
                    "Lcom/android/internal/annotations/VisibleForNative;",
                    "Lcom/android/internal/annotations/VisibleForTesting;",
                    // TODO(b/173649240): due to an oversight, some new overlaps slipped through
                    // in S.
                    "Landroid/hardware/usb/gadget/V1_1/IUsbGadget;",
                    "Landroid/hardware/usb/gadget/V1_2/GadgetFunction;",
                    "Landroid/hardware/usb/gadget/V1_2/IUsbGadget;",
                    "Landroid/hardware/usb/gadget/V1_2/IUsbGadgetCallback;",
                    "Landroid/hardware/usb/gadget/V1_2/UsbSpeed;",
                    "Landroid/os/BlockUntrustedTouchesMode;",
                    "Landroid/os/CreateAppDataArgs;",
                    "Landroid/os/CreateAppDataResult;",
                    "Landroid/os/IInputConstants;",
                    "Landroid/os/InputEventInjectionResult;",
                    "Landroid/os/InputEventInjectionSync;",
                    "Lcom/android/internal/util/FrameworkStatsLog;"
            );

    /**
     * TODO(b/199529199): Address these.
     * List of duplicate classes between bootclasspath and shared libraries.
     *
     * <p> DO NOT ADD CLASSES TO THIS LIST!
     */
    private static final Set<String> BCP_AND_SHARED_LIB_BURNDOWN_LIST =
                ImmutableSet.of(
                    "Landroid/hidl/base/V1_0/DebugInfo;",
                    "Landroid/hidl/base/V1_0/IBase;",
                    "Landroid/hidl/manager/V1_0/IServiceManager;",
                    "Landroid/hidl/manager/V1_0/IServiceNotification;",
                    "Landroidx/annotation/Keep;",
                    "Lcom/google/android/embms/nano/EmbmsProtos;",
                    "Lcom/google/protobuf/nano/android/ParcelableExtendableMessageNano;",
                    "Lcom/google/protobuf/nano/android/ParcelableMessageNano;",
                    "Lcom/google/protobuf/nano/android/ParcelableMessageNanoCreator;",
                    "Lcom/google/protobuf/nano/CodedInputByteBufferNano;",
                    "Lcom/google/protobuf/nano/CodedOutputByteBufferNano;",
                    "Lcom/google/protobuf/nano/ExtendableMessageNano;",
                    "Lcom/google/protobuf/nano/Extension;",
                    "Lcom/google/protobuf/nano/FieldArray;",
                    "Lcom/google/protobuf/nano/FieldData;",
                    "Lcom/google/protobuf/nano/InternalNano;",
                    "Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException;",
                    "Lcom/google/protobuf/nano/MapFactories;",
                    "Lcom/google/protobuf/nano/MessageNano;",
                    "Lcom/google/protobuf/nano/MessageNanoPrinter;",
                    "Lcom/google/protobuf/nano/UnknownFieldData;",
                    "Lcom/google/protobuf/nano/WireFormatNano;",
                    "Lcom/qualcomm/qcrilhook/BaseQmiTypes;",
                    "Lcom/qualcomm/qcrilhook/CSignalStrength;",
                    "Lcom/qualcomm/qcrilhook/EmbmsOemHook;",
                    "Lcom/qualcomm/qcrilhook/EmbmsProtoUtils;",
                    "Lcom/qualcomm/qcrilhook/IOemHookCallback;",
                    "Lcom/qualcomm/qcrilhook/IQcRilHook;",
                    "Lcom/qualcomm/qcrilhook/IQcRilHookExt;",
                    "Lcom/qualcomm/qcrilhook/OemHookCallback;",
                    "Lcom/qualcomm/qcrilhook/PresenceMsgBuilder;",
                    "Lcom/qualcomm/qcrilhook/PresenceMsgParser;",
                    "Lcom/qualcomm/qcrilhook/PresenceOemHook;",
                    "Lcom/qualcomm/qcrilhook/PrimitiveParser;",
                    "Lcom/qualcomm/qcrilhook/QcRilHook;",
                    "Lcom/qualcomm/qcrilhook/QcRilHookCallback;",
                    "Lcom/qualcomm/qcrilhook/QcRilHookCallbackExt;",
                    "Lcom/qualcomm/qcrilhook/QcRilHookExt;",
                    "Lcom/qualcomm/qcrilhook/QmiOemHook;",
                    "Lcom/qualcomm/qcrilhook/QmiOemHookConstants;",
                    "Lcom/qualcomm/qcrilhook/QmiPrimitiveTypes;",
                    "Lcom/qualcomm/qcrilhook/TunerOemHook;",
                    "Lcom/qualcomm/qcrilmsgtunnel/IQcrilMsgTunnel;",
                    "Lcom/qualcomm/utils/CommandException;",
                    "Lcom/qualcomm/utils/RILConstants;",
                    "Lorg/chromium/net/ApiVersion;",
                    "Lorg/chromium/net/BidirectionalStream;",
                    "Lorg/chromium/net/CallbackException;",
                    "Lorg/chromium/net/CronetEngine;",
                    "Lorg/chromium/net/CronetException;",
                    "Lorg/chromium/net/CronetProvider;",
                    "Lorg/chromium/net/EffectiveConnectionType;",
                    "Lorg/chromium/net/ExperimentalBidirectionalStream;",
                    "Lorg/chromium/net/ExperimentalCronetEngine;",
                    "Lorg/chromium/net/ExperimentalUrlRequest;",
                    "Lorg/chromium/net/ICronetEngineBuilder;",
                    "Lorg/chromium/net/InlineExecutionProhibitedException;",
                    "Lorg/chromium/net/NetworkException;",
                    "Lorg/chromium/net/NetworkQualityRttListener;",
                    "Lorg/chromium/net/NetworkQualityThroughputListener;",
                    "Lorg/chromium/net/QuicException;",
                    "Lorg/chromium/net/RequestFinishedInfo;",
                    "Lorg/chromium/net/RttThroughputValues;",
                    "Lorg/chromium/net/ThreadStatsUid;",
                    "Lorg/chromium/net/UploadDataProvider;",
                    "Lorg/chromium/net/UploadDataProviders;",
                    "Lorg/chromium/net/UploadDataSink;",
                    "Lorg/chromium/net/UrlRequest;",
                    "Lorg/chromium/net/UrlResponseInfo;"
                );
    /**
     * Ensure that there are no duplicate classes among jars listed in BOOTCLASSPATH.
     */
    @Test
    public void testBootclasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(ApiLevelUtil.isAfter(getDevice(), 29));
        ImmutableList<String> jars =
                Classpaths.getJarsOnClasspath(getDevice(), BOOTCLASSPATH);
        assertThat(getDuplicateClasses(jars)).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testSystemServerClasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(ApiLevelUtil.isAfter(getDevice(), 29));
        ImmutableList<String> jars =
                Classpaths.getJarsOnClasspath(getDevice(), SYSTEMSERVERCLASSPATH);
        assertThat(getDuplicateClasses(jars)).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in BOOTCLASSPATH and
     * SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testBootClasspathAndSystemServerClasspath_nonDuplicateClasses() throws Exception {
        assumeTrue(ApiLevelUtil.isAfter(getDevice(), 29));
        ImmutableList.Builder<String> jars = ImmutableList.builder();
        jars.addAll(Classpaths.getJarsOnClasspath(getDevice(), BOOTCLASSPATH));
        jars.addAll(Classpaths.getJarsOnClasspath(getDevice(), SYSTEMSERVERCLASSPATH));

        Multimap<String, String> duplicates = getDuplicateClasses(jars.build());
        Multimap<String, String> filtered = Multimaps.filterKeys(duplicates,
                duplicate -> !BCP_AND_SSCP_OVERLAP_BURNDOWN_LIST.contains(duplicate));

        assertThat(filtered).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among APEX jars listed in BOOTCLASSPATH.
     */
    @Test
    public void testBootClasspath_nonDuplicateApexJarClasses() throws Exception {
        ImmutableList<String> jars =
                Classpaths.getJarsOnClasspath(getDevice(), BOOTCLASSPATH);

        Multimap<String, String> duplicates = getDuplicateClasses(jars);
        Multimap<String, String> filtered =
                Multimaps.filterValues(duplicates, jar -> jar.startsWith("/apex/"));

        assertThat(filtered).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among APEX jars listed in SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testSystemServerClasspath_nonDuplicateApexJarClasses() throws Exception {
        ImmutableList<String> jars =
                Classpaths.getJarsOnClasspath(getDevice(), SYSTEMSERVERCLASSPATH);

        Multimap<String, String> duplicates = getDuplicateClasses(jars);
        Multimap<String, String> filtered =
                Multimaps.filterValues(duplicates, jar -> jar.startsWith("/apex/"));

        assertThat(filtered).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among APEX jars listed in BOOTCLASSPATH and
     * SYSTEMSERVERCLASSPATH.
     */
    @Test
    public void testBootClasspathAndSystemServerClasspath_nonApexDuplicateClasses()
            throws Exception {
        ImmutableList.Builder<String> jars = ImmutableList.builder();
        jars.addAll(Classpaths.getJarsOnClasspath(getDevice(), BOOTCLASSPATH));
        jars.addAll(Classpaths.getJarsOnClasspath(getDevice(), SYSTEMSERVERCLASSPATH));

        Multimap<String, String> duplicates = getDuplicateClasses(jars.build());
        Multimap<String, String> filtered = Multimaps.filterKeys(duplicates,
                duplicate -> !BCP_AND_SSCP_OVERLAP_BURNDOWN_LIST.contains(duplicate));
        filtered = Multimaps.filterValues(filtered, jar -> jar.startsWith("/apex/"));

        assertThat(filtered).isEmpty();
    }

    /**
     * Ensure that there are no duplicate classes among jars listed in BOOTCLASSPATH and
     * shared library jars.
     */
    @Test
    public void testBootClasspathAndSharedLibs_nonDuplicateClasses() throws Exception {
        assumeTrue(ApiLevelUtil.isAfter(getDevice(), 29));
        final ImmutableList.Builder<String> jars = ImmutableList.builder();
        final ImmutableList<SharedLibraryInfo> sharedLibs =
                Classpaths.getSharedLibraryInfos(getDevice(), getBuild());
        jars.addAll(Classpaths.getJarsOnClasspath(getDevice(), BOOTCLASSPATH));
        jars.addAll(sharedLibs.stream()
                .map(sharedLibraryInfo -> sharedLibraryInfo.paths)
                .flatMap(ImmutableCollection::stream)
                .filter(this::doesFileExist)
                .collect(ImmutableList.toImmutableList())
        );
        final Multimap<String, String> duplicates = getDuplicateClasses(jars.build());
        final Multimap<String, String> filtered = Multimaps.filterKeys(duplicates,
            duplicate -> !BCP_AND_SHARED_LIB_BURNDOWN_LIST.contains(duplicate)
                         && !isSameLibrary(duplicates.get(duplicate), sharedLibs)
        );
        assertThat(filtered).isEmpty();
    }

    /**
     * Gets the duplicate classes within a list of jar files.
     *
     * @param jars a list of jar files.
     * @return a multimap with the class name as a key and the jar files as a value.
     */
    private Multimap<String, String> getDuplicateClasses(ImmutableCollection<String> jars)
            throws Exception {
        final Multimap<String, String> allClasses = HashMultimap.create();
        jars.stream()
                .parallel()
                .forEach(jar -> {
                    try {
                        Classpaths.getClassDefsFromJar(getDevice(), jar)
                                .stream()
                                // Inner classes always go with their parent.
                                .filter(classDef -> !classDef.getType().contains("$"))
                                .forEach(classDef -> {
                                    synchronized (allClasses) {
                                        allClasses.put(classDef.getType(), jar);
                                    }
                                });
                    } catch (DeviceNotAvailableException | IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        return Multimaps.filterKeys(allClasses, key -> allClasses.get(key).size() > 1);
    }

    private boolean doesFileExist(String path) {
        assertThat(path).isNotNull();
        try {
            return getDevice().doesFileExist(path);
        } catch(DeviceNotAvailableException e) {
            throw new RuntimeException("Could not check whether " + path + " exists on device", e);
        }
    }

    /**
     * Get the name of a shared library.
     *
     * @return the shared library name or the jar's path if it's not a shared library.
     */
    private String getSharedLibraryNameOrPath(String jar,
                ImmutableList<SharedLibraryInfo> sharedLibs) {
        return sharedLibs.stream()
                         .filter(sharedLib -> sharedLib.paths.contains(jar))
                         .map(sharedLib -> sharedLib.name)
                         .findFirst().orElse(jar);
    }

    /**
     * Check whether a list of jars are all different versions of the same library.
     */
    private boolean isSameLibrary(Collection<String> jars,
                ImmutableList<SharedLibraryInfo> sharedLibs) {
        return jars.stream()
                   .map(jar -> getSharedLibraryNameOrPath(jar, sharedLibs))
                   .distinct()
                   .count() == 1;
    }
}
