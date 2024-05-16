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

package android.app.appops.cts

import android.app.AppOpsManager
import android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
import android.content.pm.PermissionInfo.PROTECTION_FLAG_APPOP
import android.platform.test.annotations.AppModeFull
import android.util.ArrayMap
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth
import org.junit.Assert.fail
import org.junit.Test

@AppModeFull(reason = "Need to get system permission info")
class AppOpDefinitionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ensureRuntimeAppOpMappingIsCorrect() {
        val missingPerms = mutableListOf<Triple<String, String, String>>()
        val opStrs = AppOpsManager.getOpStrs()
        for (opCode in 0 until AppOpsManager.getNumOps()) {
            val opStr = opStrs[opCode]
            val permission = AppOpsManager.opToPermission(opCode) ?: continue
            val permissionInfo = context.packageManager.getPermissionInfo(permission, 0)
            val isAppOp = (permissionInfo.protectionLevel and PROTECTION_FLAG_APPOP) != 0
            val isRuntime = permissionInfo.protection == PROTECTION_DANGEROUS
            if ((!isRuntime && !isAppOp) || AppOpsManager.permissionToOp(permission) != null) {
                continue
            }

            val permType = if (isRuntime) RUNTIME else APPOP
            missingPerms.add(Triple(opStr, permission, permType))
        }

        if (missingPerms.isEmpty()) {
            return
        }

        val message = StringBuilder()
        for ((opStr, perm, permType) in missingPerms) {
            message.append("$opStr missing mapping to $permType permission $perm \n")
        }
        fail(message.toString())
    }

    @Test
    fun ensureAppOpsDefinitionsAreCorrect() {
        val frameworkOpNames = AppOpsManager.getOpStrs().toSet()
        for ((opName, opCode) in APP_OPS) {
            Truth.assertThat(frameworkOpNames).contains(opName)
            Truth.assertWithMessage("Op mismatch, appop : $opName, $opCode")
                .that(AppOpsManager.strOpToOp(opName)).isEqualTo(opCode)
        }
    }

    companion object {
        private const val RUNTIME = "runtime"
        private const val APPOP = "appop"

        private val APP_OPS = ArrayMap<String, Int>()
        init {
            APP_OPS[AppOpsManager.OPSTR_COARSE_LOCATION] = 0
            APP_OPS[AppOpsManager.OPSTR_FINE_LOCATION] = 1
            APP_OPS[AppOpsManager.OPSTR_GPS] = 2
            APP_OPS[AppOpsManager.OPSTR_VIBRATE] = 3
            APP_OPS[AppOpsManager.OPSTR_READ_CONTACTS] = 4
            APP_OPS[AppOpsManager.OPSTR_WRITE_CONTACTS] = 5
            APP_OPS[AppOpsManager.OPSTR_READ_CALL_LOG] = 6
            APP_OPS[AppOpsManager.OPSTR_WRITE_CALL_LOG] = 7
            APP_OPS[AppOpsManager.OPSTR_READ_CALENDAR] = 8
            APP_OPS[AppOpsManager.OPSTR_WRITE_CALENDAR] = 9
            APP_OPS[AppOpsManager.OPSTR_WIFI_SCAN] = 10
            APP_OPS[AppOpsManager.OPSTR_POST_NOTIFICATION] = 11
            APP_OPS[AppOpsManager.OPSTR_NEIGHBORING_CELLS] = 12
            APP_OPS[AppOpsManager.OPSTR_CALL_PHONE] = 13
            APP_OPS[AppOpsManager.OPSTR_READ_SMS] = 14
            APP_OPS[AppOpsManager.OPSTR_WRITE_SMS] = 15
            APP_OPS[AppOpsManager.OPSTR_RECEIVE_SMS] = 16
            APP_OPS[AppOpsManager.OPSTR_RECEIVE_EMERGENCY_BROADCAST] = 17
            APP_OPS[AppOpsManager.OPSTR_RECEIVE_MMS] = 18
            APP_OPS[AppOpsManager.OPSTR_RECEIVE_WAP_PUSH] = 19
            APP_OPS[AppOpsManager.OPSTR_SEND_SMS] = 20
            APP_OPS[AppOpsManager.OPSTR_READ_ICC_SMS] = 21
            APP_OPS[AppOpsManager.OPSTR_WRITE_ICC_SMS] = 22
            APP_OPS[AppOpsManager.OPSTR_WRITE_SETTINGS] = 23
            APP_OPS[AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW] = 24
            APP_OPS[AppOpsManager.OPSTR_ACCESS_NOTIFICATIONS] = 25
            APP_OPS[AppOpsManager.OPSTR_CAMERA] = 26
            APP_OPS[AppOpsManager.OPSTR_RECORD_AUDIO] = 27
            APP_OPS[AppOpsManager.OPSTR_PLAY_AUDIO] = 28
            APP_OPS[AppOpsManager.OPSTR_READ_CLIPBOARD] = 29
            APP_OPS[AppOpsManager.OPSTR_WRITE_CLIPBOARD] = 30
            APP_OPS[AppOpsManager.OPSTR_TAKE_MEDIA_BUTTONS] = 31
            APP_OPS[AppOpsManager.OPSTR_TAKE_AUDIO_FOCUS] = 32
            APP_OPS[AppOpsManager.OPSTR_AUDIO_MASTER_VOLUME] = 33
            APP_OPS[AppOpsManager.OPSTR_AUDIO_VOICE_VOLUME] = 34
            APP_OPS[AppOpsManager.OPSTR_AUDIO_RING_VOLUME] = 35
            APP_OPS[AppOpsManager.OPSTR_AUDIO_MEDIA_VOLUME] = 36
            APP_OPS[AppOpsManager.OPSTR_AUDIO_ALARM_VOLUME] = 37
            APP_OPS[AppOpsManager.OPSTR_AUDIO_NOTIFICATION_VOLUME] = 38
            APP_OPS[AppOpsManager.OPSTR_AUDIO_BLUETOOTH_VOLUME] = 39
            APP_OPS[AppOpsManager.OPSTR_WAKE_LOCK] = 40
            APP_OPS[AppOpsManager.OPSTR_MONITOR_LOCATION] = 41
            APP_OPS[AppOpsManager.OPSTR_MONITOR_HIGH_POWER_LOCATION] = 42
            APP_OPS[AppOpsManager.OPSTR_GET_USAGE_STATS] = 43
            APP_OPS[AppOpsManager.OPSTR_MUTE_MICROPHONE] = 44
            APP_OPS[AppOpsManager.OPSTR_TOAST_WINDOW] = 45
            APP_OPS[AppOpsManager.OPSTR_PROJECT_MEDIA] = 46
            APP_OPS[AppOpsManager.OPSTR_ACTIVATE_VPN] = 47
            APP_OPS[AppOpsManager.OPSTR_WRITE_WALLPAPER] = 48
            APP_OPS[AppOpsManager.OPSTR_ASSIST_STRUCTURE] = 49
            APP_OPS[AppOpsManager.OPSTR_ASSIST_SCREENSHOT] = 50
            APP_OPS[AppOpsManager.OPSTR_READ_PHONE_STATE] = 51
            APP_OPS[AppOpsManager.OPSTR_ADD_VOICEMAIL] = 52
            APP_OPS[AppOpsManager.OPSTR_USE_SIP] = 53
            APP_OPS[AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS] = 54
            APP_OPS[AppOpsManager.OPSTR_USE_FINGERPRINT] = 55
            APP_OPS[AppOpsManager.OPSTR_BODY_SENSORS] = 56
            APP_OPS[AppOpsManager.OPSTR_READ_CELL_BROADCASTS] = 57
            APP_OPS[AppOpsManager.OPSTR_MOCK_LOCATION] = 58
            APP_OPS[AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE] = 59
            APP_OPS[AppOpsManager.OPSTR_WRITE_EXTERNAL_STORAGE] = 60
            APP_OPS[AppOpsManager.OPSTR_TURN_SCREEN_ON] = 61
            APP_OPS[AppOpsManager.OPSTR_GET_ACCOUNTS] = 62
            APP_OPS[AppOpsManager.OPSTR_RUN_IN_BACKGROUND] = 63
            APP_OPS[AppOpsManager.OPSTR_AUDIO_ACCESSIBILITY_VOLUME] = 64
            APP_OPS[AppOpsManager.OPSTR_READ_PHONE_NUMBERS] = 65
            APP_OPS[AppOpsManager.OPSTR_REQUEST_INSTALL_PACKAGES] = 66
            APP_OPS[AppOpsManager.OPSTR_PICTURE_IN_PICTURE] = 67
            APP_OPS[AppOpsManager.OPSTR_INSTANT_APP_START_FOREGROUND] = 68
            APP_OPS[AppOpsManager.OPSTR_ANSWER_PHONE_CALLS] = 69
            APP_OPS[AppOpsManager.OPSTR_RUN_ANY_IN_BACKGROUND] = 70
            APP_OPS[AppOpsManager.OPSTR_CHANGE_WIFI_STATE] = 71
            APP_OPS[AppOpsManager.OPSTR_REQUEST_DELETE_PACKAGES] = 72
            APP_OPS[AppOpsManager.OPSTR_BIND_ACCESSIBILITY_SERVICE] = 73
            APP_OPS[AppOpsManager.OPSTR_ACCEPT_HANDOVER] = 74
            APP_OPS[AppOpsManager.OPSTR_MANAGE_IPSEC_TUNNELS] = 75
            APP_OPS[AppOpsManager.OPSTR_START_FOREGROUND] = 76
            APP_OPS[AppOpsManager.OPSTR_BLUETOOTH_SCAN] = 77
            APP_OPS[AppOpsManager.OPSTR_USE_BIOMETRIC] = 78
            APP_OPS[AppOpsManager.OPSTR_ACTIVITY_RECOGNITION] = 79
            APP_OPS[AppOpsManager.OPSTR_SMS_FINANCIAL_TRANSACTIONS] = 80
            APP_OPS[AppOpsManager.OPSTR_READ_MEDIA_AUDIO] = 81
            APP_OPS[AppOpsManager.OPSTR_WRITE_MEDIA_AUDIO] = 82
            APP_OPS[AppOpsManager.OPSTR_READ_MEDIA_VIDEO] = 83
            APP_OPS[AppOpsManager.OPSTR_WRITE_MEDIA_VIDEO] = 84
            APP_OPS[AppOpsManager.OPSTR_READ_MEDIA_IMAGES] = 85
            APP_OPS[AppOpsManager.OPSTR_WRITE_MEDIA_IMAGES] = 86
            APP_OPS[AppOpsManager.OPSTR_LEGACY_STORAGE] = 87
            APP_OPS[AppOpsManager.OPSTR_ACCESS_ACCESSIBILITY] = 88
            APP_OPS[AppOpsManager.OPSTR_READ_DEVICE_IDENTIFIERS] = 89
            APP_OPS[AppOpsManager.OPSTR_ACCESS_MEDIA_LOCATION] = 90
            APP_OPS[AppOpsManager.OPSTR_QUERY_ALL_PACKAGES] = 91
            APP_OPS[AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE] = 92
            APP_OPS[AppOpsManager.OPSTR_INTERACT_ACROSS_PROFILES] = 93
            APP_OPS[AppOpsManager.OPSTR_ACTIVATE_PLATFORM_VPN] = 94
            APP_OPS[AppOpsManager.OPSTR_LOADER_USAGE_STATS] = 95
            // Op 96 was deprecated and removed, OP_NONE uses index 96 now
            APP_OPS[""] = 96
            APP_OPS[AppOpsManager.OPSTR_AUTO_REVOKE_PERMISSIONS_IF_UNUSED] = 97
            APP_OPS[AppOpsManager.OPSTR_AUTO_REVOKE_MANAGED_BY_INSTALLER] = 98
            APP_OPS[AppOpsManager.OPSTR_NO_ISOLATED_STORAGE] = 99
            APP_OPS[AppOpsManager.OPSTR_PHONE_CALL_MICROPHONE] = 100
            APP_OPS[AppOpsManager.OPSTR_PHONE_CALL_CAMERA] = 101
            APP_OPS[AppOpsManager.OPSTR_RECORD_AUDIO_HOTWORD] = 102
            APP_OPS[AppOpsManager.OPSTR_MANAGE_ONGOING_CALLS] = 103
            APP_OPS[AppOpsManager.OPSTR_MANAGE_CREDENTIALS] = 104
            APP_OPS[AppOpsManager.OPSTR_USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER] = 105
            APP_OPS[AppOpsManager.OPSTR_RECORD_AUDIO_OUTPUT] = 106
            APP_OPS[AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM] = 107
            APP_OPS[AppOpsManager.OPSTR_FINE_LOCATION_SOURCE] = 108
            APP_OPS[AppOpsManager.OPSTR_COARSE_LOCATION_SOURCE] = 109
            APP_OPS[AppOpsManager.OPSTR_MANAGE_MEDIA] = 110
            APP_OPS[AppOpsManager.OPSTR_BLUETOOTH_CONNECT] = 111
            APP_OPS[AppOpsManager.OPSTR_UWB_RANGING] = 112
            APP_OPS[AppOpsManager.OPSTR_ACTIVITY_RECOGNITION_SOURCE] = 113
            APP_OPS[AppOpsManager.OPSTR_BLUETOOTH_ADVERTISE] = 114
            APP_OPS[AppOpsManager.OPSTR_RECORD_INCOMING_PHONE_AUDIO] = 115
            APP_OPS[AppOpsManager.OPSTR_NEARBY_WIFI_DEVICES] = 116
            APP_OPS[AppOpsManager.OPSTR_ESTABLISH_VPN_SERVICE] = 117
            APP_OPS[AppOpsManager.OPSTR_ESTABLISH_VPN_MANAGER] = 118
            APP_OPS[AppOpsManager.OPSTR_ACCESS_RESTRICTED_SETTINGS] = 119
            APP_OPS[AppOpsManager.OPSTR_RECEIVE_AMBIENT_TRIGGER_AUDIO] = 120
            APP_OPS[AppOpsManager.OPSTR_RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO] = 121
            APP_OPS[AppOpsManager.OPSTR_RUN_USER_INITIATED_JOBS] = 122
            APP_OPS[AppOpsManager.OPSTR_READ_MEDIA_VISUAL_USER_SELECTED] = 123
            APP_OPS[AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_SUSPENSION] = 124
            APP_OPS[AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_DISMISSIBLE_NOTIFICATIONS] = 125
            APP_OPS[AppOpsManager.OPSTR_READ_WRITE_HEALTH_DATA] = 126
            APP_OPS[AppOpsManager.OPSTR_FOREGROUND_SERVICE_SPECIAL_USE] = 127
            APP_OPS[AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS] = 128
            APP_OPS[AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_HIBERNATION] = 129
            APP_OPS[AppOpsManager.OPSTR_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION] = 130
            APP_OPS[AppOpsManager.OPSTR_CAPTURE_CONSENTLESS_BUGREPORT_ON_USERDEBUG_BUILD] = 131
            APP_OPS[AppOpsManager.OPSTR_DEPRECATED_2] = 132
            APP_OPS[AppOpsManager.OPSTR_USE_FULL_SCREEN_INTENT] = 133
            APP_OPS[AppOpsManager.OPSTR_CAMERA_SANDBOXED] = 134
            APP_OPS[AppOpsManager.OPSTR_RECORD_AUDIO_SANDBOXED] = 135
            APP_OPS[AppOpsManager.OPSTR_RECEIVE_SANDBOX_TRIGGER_AUDIO] = 136
            APP_OPS[AppOpsManager.OPSTR_DEPRECATED_3] = 137
            APP_OPS[AppOpsManager.OPSTR_CREATE_ACCESSIBILITY_OVERLAY] = 138
            APP_OPS[AppOpsManager.OPSTR_MEDIA_ROUTING_CONTROL] = 139
            APP_OPS[AppOpsManager.OPSTR_ENABLE_MOBILE_DATA_BY_USER] = 140
            APP_OPS[AppOpsManager.OPSTR_RESERVED_FOR_TESTING] = 141
            APP_OPS[AppOpsManager.OPSTR_RAPID_CLEAR_NOTIFICATIONS_BY_LISTENER] = 142
            APP_OPS[AppOpsManager.OPSTR_READ_SYSTEM_GRAMMATICAL_GENDER] = 143
            APP_OPS[AppOpsManager.OPSTR_RUN_BACKUP_JOBS] = 144
            APP_OPS[AppOpsManager.OPSTR_ARCHIVE_ICON_OVERLAY] = 145
            APP_OPS[AppOpsManager.OPSTR_UNARCHIVAL_CONFIRMATION] = 146
            APP_OPS[AppOpsManager.OPSTR_EMERGENCY_LOCATION] = 147
        }
    }
}
