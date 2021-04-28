/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims.cts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.service.carrier.CarrierService;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.SipMessage;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ImsUtils {
    public static final boolean VDBG = false;

    // ImsService rebind has an exponential backoff capping at 64 seconds. Wait for 70 seconds to
    // allow for the new poll to happen in the framework.
    public static final int TEST_TIMEOUT_MS = 70000;

    // Id for non compressed auto configuration xml.
    public static final int ITEM_NON_COMPRESSED = 2000;
    // Id for compressed auto configuration xml.
    public static final int ITEM_COMPRESSED = 2001;
    // TODO Replace with a real sip message once that logic is in.
    public static final String TEST_TRANSACTION_ID = "z9hG4bK.TeSt";
    public static final String TEST_CALL_ID = "a84b4c76e66710@pc33.atlanta.com";

    // Sample messages from RFC 3261 modified for parsing use cases.
    public static final SipMessage TEST_SIP_MESSAGE = new SipMessage(
            "INVITE sip:bob@biloxi.com SIP/2.0",
            // Typical Via
            "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK.TeSt\n"
                    + "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 0",
            new byte[0]);

    // Example from RFC 3665
    public static final SipMessage TEST_SIP_REGISTER = new SipMessage(
            "REGISTER sips:ss2.biloxi.example.com SIP/2.0",
            "Via: SIP/2.0/TLS client.biloxi.example.com:5061;branch=z9hG4bKnashds7\n"
                    + "Max-Forwards: 70\n"
                    + "From: Bob <sips:bob@biloxi.example.com>;tag=a73kszlfl\n"
                    + "To: Bob <sips:bob@biloxi.example.com>\n"
                    + "Call-ID: 1j9FpLxk3uxtm8tn@biloxi.example.com\n"
                    + "CSeq: 1 REGISTER\n"
                    + "Contact: <sips:bob@client.biloxi.example.com>\n"
                    + "Content-Length: 0",
            new byte[0]);

    //Example from RFC3903, but does not include PIDF document.
    public static final SipMessage TEST_SIP_PUBLISH = new SipMessage(
            "PUBLISH sip:presentity@example.com SIP/2.0",
            "Via: SIP/2.0/UDP pua.example.com;branch=z9hG4bKcdad2\n"
                    + "To: <sip:presentity@example.com>\n"
                    + "From: <sip:presentity@example.com>;tag=54321mm\n"
                    + "Call-ID: 5566778@pua.example.com\n"
                    + "CSeq: 1 PUBLISH\n"
                    + "Max-Forwards: 70\n"
                    + "Expires: 3600\n"
                    + "Event: presence\n"
                    + "Content-Type: application/pidf+xml",
            new byte[0]);

    //Example from RFC3856
    public static final SipMessage TEST_SIP_SUBSCRIBE_PRESENCE = new SipMessage(
            "SUBSCRIBE sip:resource@example.com SIP/2.0",
            "Via: SIP/2.0/TCP watcherhost.example.com;branch=z9hG4bKnashds7\n"
                    + "To: <sip:resource@example.com>\n"
                    + "From: <sip:user@example.com>;tag=xfg9\n"
                    + "Call-ID: 2010@watcherhost.example.com\n"
                    + "CSeq: 17766 SUBSCRIBE\n"
                    + "Max-Forwards: 70\n"
                    + "Event: presence\n"
                    + "Accept: application/pidf+xml\n"
                    + "Contact: <sip:user@watcherhost.example.com>\n"
                    + "Expires: 600\n"
                    + "Content-Length: 0",
            new byte[0]);

    //Example from RFC3261
    public static final SipMessage TEST_SIP_OPTIONS = new SipMessage(
            "OPTIONS sip:carol@chicago.com SIP/2.0",
            "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKhjhs8ass877\n"
                    + "Max-Forwards: 70\n"
                    + "To: <sip:carol@chicago.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: a84b4c76e66710\n"
                    + "CSeq: 63104 OPTIONS\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Accept: application/sdp\n"
                    + "Content-Length: 0",
            new byte[0]);

    // Sample message from RFC 3261
    public static final SipMessage TEST_SIP_MESSAGE_INVALID_REQUEST = new SipMessage(
            "INVITE sip:bob@biloxi.comSIP/2.0",
            // Typical Via
            "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK.TeSt\n"
                    + "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 142",
            new byte[0]);

    // Sample message from RFC 3261
    public static final SipMessage TEST_SIP_MESSAGE_INVALID_RESPONSE = new SipMessage(
            "SIP/2.0 200OK",
            "Via: SIP/2.0/TCP terminal.vancouver.example.com;"
                    + "branch=z9hG4bKwYb6QREiCL\n"
                    + "To: <sip:adam-buddies@pres.vancouver.example.com>;tag=zpNctbZq\n"
                    + "From: <sip:adam@vancouver.example.com>;tag=ie4hbb8t\n"
                    + "Call-ID: cdB34qLToC@terminal.vancouver.example.com\n"
                    + "CSeq: 322723822 SUBSCRIBE\n"
                    + "Contact: <sip:pres.vancouver.example.com>\n"
                    + "Expires: 7200\n"
                    + "Require: eventlist\n"
                    + "Content-Length: 0",
            new byte[0]);

    public static boolean shouldTestTelephony() {
        final PackageManager pm = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    public static boolean shouldTestImsService() {
        final PackageManager pm = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager();
        boolean hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        boolean hasIms = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
        return hasTelephony && hasIms;
    }

    public static boolean shouldTestImsSingleRegistration() {
        final PackageManager pm = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager();
        boolean hasIms = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
        boolean hasSingleReg = pm.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_IMS_SINGLE_REGISTRATION);
        return hasIms && hasSingleReg;
    }

    public static int getPreferredActiveSubId() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        SubscriptionManager sm = (SubscriptionManager) context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        List<SubscriptionInfo> infos = ShellIdentityUtils.invokeMethodWithShellPermissions(sm,
                SubscriptionManager::getActiveSubscriptionInfoList);

        int defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isSubIdInInfoList(infos, defaultSubId)) {
            return defaultSubId;
        }

        defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
        if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isSubIdInInfoList(infos, defaultSubId)) {
            return defaultSubId;
        }

        // Couldn't resolve a default. We can try to resolve a default using the active
        // subscriptions.
        if (!infos.isEmpty()) {
            return infos.get(0).getSubscriptionId();
        }
        // There must be at least one active subscription.
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private static boolean isSubIdInInfoList(List<SubscriptionInfo> infos, int subId) {
        return infos.stream().anyMatch(info -> info.getSubscriptionId() == subId);
    }

    /**
     * If a carrier app implements CarrierMessagingService it can choose to take care of handling
     * SMS OTT so SMS over IMS APIs won't be triggered which would be WAI so we do not run the tests
     * if there exist a carrier app that declares a CarrierMessagingService
     */
    public static boolean shouldRunSmsImsTests(int subId) {
        if (!shouldTestImsService()) {
            return false;
        }
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        TelephonyManager tm =
                (TelephonyManager) InstrumentationRegistry.getInstrumentation().getContext()
                        .getSystemService(Context.TELEPHONY_SERVICE);
        tm = tm.createForSubscriptionId(subId);
        final long token = Binder.clearCallingIdentity();
        List<String> carrierPackages;
        try {
            carrierPackages = tm.getCarrierPackageNamesForIntent(
                    new Intent(CarrierService.CARRIER_SERVICE_INTERFACE));
        } finally {
            Binder.restoreCallingIdentity(token);
        }

        if (carrierPackages == null || carrierPackages.size() == 0) {
            return true;
        }
        final PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent("android.service.carrier.CarrierMessagingService");
        List<ResolveInfo> resolveInfos = packageManager.queryIntentServices(intent, 0);
        for (ResolveInfo info : resolveInfos) {
            if (carrierPackages.contains(info.serviceInfo.packageName)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Retry every 5 seconds until the condition is true or fail after TEST_TIMEOUT_MS seconds.
     */
    public static boolean retryUntilTrue(Callable<Boolean> condition) throws Exception {
        return retryUntilTrue(condition, TEST_TIMEOUT_MS, 14 /*numTries*/);
    }

    /**
     * Retry every timeoutMs/numTimes until the condition is true or fail if the condition is never
     * met.
     */
    public static boolean retryUntilTrue(Callable<Boolean> condition,
            int timeoutMs, int numTimes) throws Exception {
        int sleepTime = timeoutMs / numTimes;
        int retryCounter = 0;
        while (retryCounter < numTimes) {
            try {
                Boolean isSuccessful = condition.call();
                isSuccessful = (isSuccessful == null) ? false : isSuccessful;
                if (isSuccessful) return true;
            } catch (Exception e) {
                // we will retry
            }
            Thread.sleep(sleepTime);
            retryCounter++;
        }
        return false;
    }

    /**
     * compress the gzip format data
     * @hide
     */
    public static byte[] compressGzip(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        byte[] out = null;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            GZIPOutputStream gzipCompressingStream =
                    new GZIPOutputStream(outputStream);
            gzipCompressingStream.write(data);
            gzipCompressingStream.close();
            out = outputStream.toByteArray();
            outputStream.close();
        } catch (IOException e) {
        }
        return out;
    }

    /**
     * decompress the gzip format data
     * @hide
     */
    public static byte[] decompressGzip(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        byte[] out = null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            GZIPInputStream gzipDecompressingStream =
                    new GZIPInputStream(inputStream);
            byte[] buf = new byte[1024];
            int size = gzipDecompressingStream.read(buf);
            while (size >= 0) {
                outputStream.write(buf, 0, size);
                size = gzipDecompressingStream.read(buf);
            }
            gzipDecompressingStream.close();
            inputStream.close();
            out = outputStream.toByteArray();
            outputStream.close();
        } catch (IOException e) {
        }
        return out;
    }
}
