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

package android.webkit.cts;

import android.os.RemoteException;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * This class serves as the public fronting API for tests to interact with the CtsTestServer.
 *
 * <p>This is a light wrapper around the Binder Proxy so that we can wrap all the RemoteExceptions
 * in a Runtime exception so that the WebServer methods can be used the same as they were used
 * previously.
 */
public final class SharedSdkWebServer {
    private final IWebServer mWebServer;

    public SharedSdkWebServer(IWebServer webServer) {
        mWebServer = webServer;
    }

    /** Starts the web server. */
    public void start(@SslMode int sslMode) {
        start(new Config().setSslMode(sslMode));
    }

    /** Starts the web server using the provided {@link Config}. */
    public void start(Config config) {
        try {
            mWebServer.start(config.mSslMode, config.mAcceptedIssuerDer);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Shuts down the web server if it was started. */
    public void shutdown() {
        try {
            mWebServer.shutdown();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Resets all request state stored. */
    public void resetRequestState() {
        try {
            mWebServer.resetRequestState();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets a response to be returned when a particular request path is passed in (with the option
     * to specify additional headers).
     */
    public String setResponse(
            String path, String responseString, List<HttpHeader> responseHeaders) {
        // We can't send a null value as a list
        // so default to an empty list if null was provided.
        if (responseHeaders == null) {
            responseHeaders = Collections.emptyList();
        }
        try {
            return mWebServer.setResponse(path, responseString, responseHeaders);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Return the absolute URL that refers to a path. */
    public String getAbsoluteUrl(String path) {
        try {
            return mWebServer.getAbsoluteUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a delayed assert url for an asset path. */
    public String getDelayedAssetUrl(String path) {
        try {
            return mWebServer.getDelayedAssetUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a url that will redirect for a path. */
    public String getRedirectingAssetUrl(String path) {
        try {
            return mWebServer.getAssetUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get the full url for an asset. */
    public String getAssetUrl(String path) {
        try {
            return mWebServer.getAssetUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get the full auth url for an asset. */
    public String getAuthAssetUrl(String path) {
        try {
            return mWebServer.getAuthAssetUrl(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a binary url. */
    public String getBinaryUrl(String mimeType, int contentLength) {
        try {
            return mWebServer.getBinaryUrl(mimeType, contentLength);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Verify if a resource was requested. */
    public boolean wasResourceRequested(String url) {
        try {
            return mWebServer.wasResourceRequested(url);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Retrieve the last request to be made on a url. */
    public HttpRequest getLastRequest(String path) {
        try {
            return mWebServer.getLastRequest(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Retrieve the last request for an asset path to be made on a url. */
    public HttpRequest getLastAssetRequest(String url) {
        try {
            return mWebServer.getLastAssetRequest(url);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /** Configuration options for starting a SharedSdkWebServer */
    public static class Config {
        private @SslMode int mSslMode;
        private @Nullable byte[] mAcceptedIssuerDer;

        public Config() {
            mSslMode = SslMode.INSECURE;
            mAcceptedIssuerDer = null;
        }

        /** Set the server's SslMode */
        public Config setSslMode(@SslMode int sslMode) {
            mSslMode = sslMode;
            return this;
        }

        /**
         * Configures the server's TrustManager to contain a given accepted issuer certificate
         * (specified as DER bytes).
         *
         * Note that this does not enforce that certificates are issued from this issuer - as with
         * the default CTS trust manager, all certificates are always considered valid. Supplying an
         * acceptedIssuer merely affects the issuer DNs contained in the certificate request sent to
         * the client in the TLS handshake.
         */
        public Config setAcceptedIssuer(@Nullable byte[] acceptedIssuerDer) {
            mAcceptedIssuerDer = acceptedIssuerDer;
            return this;
        }
    }
}
