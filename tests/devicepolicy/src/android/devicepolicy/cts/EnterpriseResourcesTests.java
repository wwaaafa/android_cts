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

package android.devicepolicy.cts;

import static android.Manifest.permission.UPDATE_DEVICE_MANAGEMENT_RESOURCES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyDrawableResource;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyStringResource;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

// TODO(b/208084779): Add more cts tests to cover setting different styles and sources, also
//  add more tests to cover calling from other packages after adding support for the new APIs in
//  the test sdk.
@RunWith(BedsteadJUnit4.class)
public class EnterpriseResourcesTests {
    private static final String TAG = "EnterpriseResourcesTests";

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sDpm =
            sContext.getSystemService(DevicePolicyManager.class);

    private static final int UPDATABLE_DRAWABLE_ID_1 = 0;
    private static final int UPDATABLE_DRAWABLE_ID_2 = 1;
    private static final int DRAWABLE_STYLE_1 = 0;

    private static final String UPDATABLE_STRING_ID_1 = "UPDATABLE_STRING_ID_1";
    private static final String UPDATABLE_STRING_ID_2 = "UPDATABLE_STRING_ID_2";

    private static final int INVALID_RESOURCE_ID = -1;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @After
    public void tearDown() {
        resetAllResources();
    }

    @Before
    public void setup() {
        resetAllResources();
    }

    private void resetAllResources() {
        try (PermissionContext p = TestApis.permissions().withPermission(
                UPDATE_DEVICE_MANAGEMENT_RESOURCES)) {
            sDpm.resetDrawables(
                    new int[]{
                            UPDATABLE_DRAWABLE_ID_1,
                            UPDATABLE_DRAWABLE_ID_2});
            sDpm.resetStrings(
                    new String[]{
                            UPDATABLE_STRING_ID_1,
                            UPDATABLE_STRING_ID_2});
        }
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDpm.setDrawables(createDrawable(
                        UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1)));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updatesCorrectUpdatableDrawable() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_1,
                DRAWABLE_STYLE_1,
                /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_updatesCurrentlyUpdatedDrawable() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_2)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_doesNotUpdateOtherUpdatableDrawables() {
        Drawable defaultDrawable = sContext.getDrawable(R.drawable.test_drawable_2);
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_2});

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_2, DRAWABLE_STYLE_1, /* default= */ () -> defaultDrawable);
        assertThat(drawable).isEqualTo(defaultDrawable);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_drawableChangedFromNull_sendsBroadcast() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setDrawables_drawableChangedFromOtherDrawable_sendsBroadcast() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void setDrawables_drawableNotChanged_doesNotSendBroadcast() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDpm.resetDrawables(
                new int[]{UPDATABLE_DRAWABLE_ID_1}));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_removesPreviouslySetDrawables() {
        Drawable defaultDrawable = sContext.getDrawable(R.drawable.test_drawable_2);
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> defaultDrawable);
        assertThat(drawable).isEqualTo(defaultDrawable);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_doesNotResetOtherSetDrawables() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_2, DRAWABLE_STYLE_1, R.drawable.test_drawable_2));

        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_2, DRAWABLE_STYLE_1, /* default= */ () -> null);
        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_2)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetDrawables_drawableChanged_sendsBroadcast() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void resetDrawables_drawableNotChanged_doesNotSendBroadcast() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_drawableIsSet_returnsUpdatedDrawable() {
        sDpm.setDrawables(createDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.drawable.test_drawable_1));

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> null);

        assertThat(areSameDrawables(drawable, sContext.getDrawable(R.drawable.test_drawable_1)))
                .isTrue();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_drawableIsNotSet_returnsDefaultDrawable() {
        Drawable defaultDrawable = sContext.getDrawable(R.drawable.test_drawable_1);
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});

        Drawable drawable = sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> defaultDrawable);

        assertThat(drawable).isEqualTo(defaultDrawable);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_defaultLoaderIsNull_throwsException() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});

        assertThrows(NullPointerException.class, () -> sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ null));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getDrawable_defaultIsNull_throwsException() {
        sDpm.resetDrawables(new int[]{UPDATABLE_DRAWABLE_ID_1});

        assertThrows(NullPointerException.class, () -> sDpm.getDrawable(
                UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, /* default= */ () -> null));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyDrawableResource_withNonExistentDrawable_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, INVALID_RESOURCE_ID));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyDrawableResource_withNonDrawableResource_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyDrawableResource(
                sContext, UPDATABLE_DRAWABLE_ID_1, DRAWABLE_STYLE_1, R.string.test_string_1));
    }

    // TODO(b/16348282): extract to a common place to make it reusable.
    private static boolean areSameDrawables(Drawable drawable1, Drawable drawable2) {
        return drawable1 == drawable2 || getBitmap(drawable1).sameAs(getBitmap(drawable2));
    }

    private static Bitmap getBitmap(Drawable drawable) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        // Some drawables have no intrinsic width - e.g. solid colours.
        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return result;
    }

    private Set<DevicePolicyDrawableResource> createDrawable(
            int updatableDrawableId, int style, int resourceId) {
        return Set.of(new DevicePolicyDrawableResource(
                sContext, updatableDrawableId, style, resourceId));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDpm.setStrings(
                createString(UPDATABLE_STRING_ID_1, R.string.test_string_1)));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_updatesCorrectUpdatableString() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        String string = sDpm.getString(UPDATABLE_STRING_ID_1, /* default= */ () -> null);
        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_updatesCurrentlyUpdatedString() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_2));

        String string = sDpm.getString(UPDATABLE_STRING_ID_1, /* default= */ () -> null);
        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_2));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_doesNotUpdateOtherUpdatableStrings() {
        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_2});
        String defaultString = sContext.getString(R.string.test_string_2);

        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        assertThat(sDpm.getString(UPDATABLE_STRING_ID_2, /* default= */ () -> defaultString))
                .isEqualTo(defaultString);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_stringChangedFromNull_sendsBroadcast() {
        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void setStrings_stringChangedFromOtherString_sendsBroadcast() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_2));

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void setStrings_stringNotChanged_doesNotSendBroadcast() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureDoesNotHavePermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1}));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_withRequiredPermission_doesNotThrowSecurityException() {
        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_removesPreviouslySetStrings() {
        String defaultString = sContext.getString(R.string.test_string_2);
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});

        assertThat(sDpm.getString(UPDATABLE_STRING_ID_1, /* default= */ () -> defaultString))
                .isEqualTo(defaultString);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_doesNotResetOtherSetStrings() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_2, R.string.test_string_2));

        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});

        String string = sDpm.getString(UPDATABLE_STRING_ID_2, /* default= */ () -> null);
        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_2));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void resetStrings_stringChanged_sendsBroadcast() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});

        broadcastReceiver.awaitForBroadcastOrFail();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    @Ignore("b/208237942")
    public void resetStrings_stringNotChanged_doesNotSendBroadcast() {
        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED);

        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});

        assertThat(broadcastReceiver.awaitForBroadcast()).isNull();
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_stringIsSet_returnsUpdatedString() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_1));

        String string = sDpm.getString(UPDATABLE_STRING_ID_1, /* default= */ () -> null);

        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_1));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_stringIsNotSet_returnsDefaultString() {
        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});
        String defaultString = sContext.getString(R.string.test_string_1);

        String string = sDpm.getString(
                UPDATABLE_STRING_ID_1, /* default= */ () -> defaultString);

        assertThat(string).isEqualTo(defaultString);
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_defaultLoadedIsNull_throwsException() {
        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});

        assertThrows(NullPointerException.class,
                () -> sDpm.getString(UPDATABLE_STRING_ID_1, /* default= */ null));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_defaultIsNull_throwsException() {
        sDpm.resetStrings(new String[]{UPDATABLE_STRING_ID_1});

        assertThrows(NullPointerException.class,
                () -> sDpm.getString(UPDATABLE_STRING_ID_1, /* default= */ () -> null));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void getString_stringWithArgs_returnsFormattedString() {
        sDpm.setStrings(createString(UPDATABLE_STRING_ID_1, R.string.test_string_with_arg));
        String testArg = "test arg";

        String string = sDpm.getString(UPDATABLE_STRING_ID_1, /* default= */ () -> null, testArg);

        assertThat(string).isEqualTo(sContext.getString(R.string.test_string_with_arg, testArg));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyStringResource_withNonExistentString_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyStringResource(
                sContext, UPDATABLE_STRING_ID_1, INVALID_RESOURCE_ID));
    }

    @Test
    @Postsubmit(reason = "New test")
    @EnsureHasPermission(UPDATE_DEVICE_MANAGEMENT_RESOURCES)
    public void constructDevicePolicyStringResource_withNonStringResource_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> new DevicePolicyStringResource(
                sContext, UPDATABLE_STRING_ID_1, R.drawable.test_drawable_2));
    }

    private Set<DevicePolicyStringResource> createString(
            String updatableStringId, int resourceId) {
        return Set.of(new DevicePolicyStringResource(
                sContext, updatableStringId, resourceId));
    }
}
