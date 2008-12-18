/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.transaction;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRouteParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.provider.Settings;
import android.util.Config;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class HttpUtils {
    private static final String TAG = "HttpUtils";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    public static final int HTTP_POST_METHOD = 1;
    public static final int HTTP_GET_METHOD = 2;

    // This is the value to use for the "Accept-Language" header.
    // Once it becomes possible for the user to change the locale
    // setting, this should no longer be static.  We should call
    // getHttpAcceptLanguage instead.
    private static final String HDR_VALUE_ACCEPT_LANGUAGE;

    static {
        HDR_VALUE_ACCEPT_LANGUAGE = getHttpAcceptLanguage();
    }

    // Definition for necessary HTTP headers.
    private static final String HDR_KEY_ACCEPT = "Accept";
    private static final String HDR_KEY_ACCEPT_LANGUAGE = "Accept-Language";
    private static final String HDR_KEY_X_WAP_PROFILE = "x-wap-profile";

    private static final String HDR_VALUE_ACCEPT =
        "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic";

    private HttpUtils() {
        // To forbidden instantiate this class.
    }

    /**
     * A helper method to send or retrieve data through HTTP protocol.
     *
     * @param token The token to identify the sending progress.
     * @param url The URL used in a GET request. Null when the method is
     *         HTTP_POST_METHOD.
     * @param pdu The data to be POST. Null when the method is HTTP_GET_METHOD.
     * @param method HTTP_POST_METHOD or HTTP_GET_METHOD.
     * @return A byte array which contains the response data.
     *         If an HTTP error code is returned, an IOException will be thrown.
     * @throws IOException if any error occurred on network interface or
     *         an HTTP error code(&gt;=400) returned from the server.
     */
    protected static byte[] httpConnection(Context context, long token,
            String url, byte[] pdu, int method, boolean isProxySet,
            String proxyHost, int proxyPort) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL must not be null.");
        }

        if (LOCAL_LOGV) {
            Log.v(TAG, "httpConnection: params list");
            Log.v(TAG, "\ttoken\t\t= " + token);
            Log.v(TAG, "\turl\t\t= " + url);
            Log.v(TAG, "\tmethod\t\t= "
                    + ((method == HTTP_POST_METHOD) ? "POST"
                            : ((method == HTTP_GET_METHOD) ? "GET" : "UNKNOWN")));
            Log.v(TAG, "\tisProxySet\t= " + isProxySet);
            Log.v(TAG, "\tproxyHost\t= " + proxyHost);
            Log.v(TAG, "\tproxyPort\t= " + proxyPort);
            // TODO Print out binary data more readable.
            //Log.v(TAG, "\tpdu\t\t= " + Arrays.toString(pdu));
        }

        AndroidHttpClient client = null;

        try {
            // Make sure to use a proxy which supports CONNECT.
            URI hostUrl = new URI(url);
            HttpHost target = new HttpHost(
                    hostUrl.getHost(), hostUrl.getPort(),
                    HttpHost.DEFAULT_SCHEME_NAME);

            client = createHttpClient();
            HttpRequest req = null;
            switch(method) {
                case HTTP_POST_METHOD:
                    ProgressCallbackEntity entity = new ProgressCallbackEntity(
                                                        context, token, pdu);
                    // Set request content type.
                    entity.setContentType("application/vnd.wap.mms-message");

                    HttpPost post = new HttpPost(url);
                    post.setEntity(entity);
                    req = post;
                    break;
                case HTTP_GET_METHOD:
                    req = new HttpGet(url);
                    break;
                default:
                    Log.e(TAG, "Unknown HTTP method: " + method
                            + ". Must be one of POST[" + HTTP_POST_METHOD
                            + "] or GET[" + HTTP_GET_METHOD + "].");
                    return null;
            }

            // Set route parameters for the request.
            HttpParams params = client.getParams();
            if (isProxySet) {
                ConnRouteParams.setDefaultProxy(
                        params, new HttpHost(proxyHost, proxyPort));
            }
            req.setParams(params);

            // Set necessary HTTP headers for MMS transmission.
            req.addHeader(HDR_KEY_ACCEPT, HDR_VALUE_ACCEPT);
            {
                String xWapProfileUrl = getXWapProfileHeaderValue(context);

                if (xWapProfileUrl != null) {
                    req.addHeader(HDR_KEY_X_WAP_PROFILE, xWapProfileUrl);
                }
            }
            req.addHeader(HDR_KEY_ACCEPT_LANGUAGE, HDR_VALUE_ACCEPT_LANGUAGE);

            HttpResponse response = client.execute(target, req);
            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != 200) { // HTTP 200 is success.
                throw new IOException("HTTP error: " + status.getReasonPhrase());
            }

            HttpEntity entity = response.getEntity();
            byte[] body = null;
            if (entity != null) {
                try {
                    if (entity.getContentLength() > 0) {
                        body = new byte[(int) entity.getContentLength()];
                        DataInputStream dis = new DataInputStream(entity.getContent());
                        try {
                            dis.readFully(body);
                        } finally {
                            try {
                                dis.close();
                            } catch (IOException e) {
                                Log.e(TAG, "Error closing input stream: " + e.getMessage());
                            }
                        }
                    }
                } finally {
                    if (entity != null) {
                        entity.consumeContent();
                    }
                }
            }
            return body;
        } catch (URISyntaxException e) {
            handleHttpConnectionException(e);
        } catch (IllegalStateException e) {
            handleHttpConnectionException(e);
        } catch (IllegalArgumentException e) {
            handleHttpConnectionException(e);
        } catch (SocketException e) {
            handleHttpConnectionException(e);
        } catch (Exception e) {
            handleHttpConnectionException(e);
        }
        finally {
            if (client != null) {
                client.close();
            }
        }
        return null;
    }

    private static String getXWapProfileHeaderValue(Context context) {
        return Settings.Gservices.getString(
                context.getContentResolver(),
                Settings.Gservices.MMS_X_WAP_PROFILE_URL);
    }

    private static void handleHttpConnectionException(Exception exception)
            throws IOException {
        // Inner exception should be logged to make life easier.
        Log.e(TAG, exception.getMessage());
        throw new IOException(exception.getMessage());
    }

    private static AndroidHttpClient createHttpClient() {
        AndroidHttpClient client
                = AndroidHttpClient.newInstance("Android-Mms/0.1");
        HttpParams params = client.getParams();
        HttpProtocolParams.setContentCharset(params, "UTF-8");
        return client;
    }

    /**
     * Return the Accept-Language header.  Use the current locale plus
     * US if we are in a different locale than US.
     */
    private static String getHttpAcceptLanguage() {
        Locale locale = Locale.getDefault();
        StringBuilder builder = new StringBuilder();

        addLocaleToHttpAcceptLanguage(builder, locale);
        if (!locale.equals(Locale.US)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            addLocaleToHttpAcceptLanguage(builder, Locale.US);
        }
        return builder.toString();
    }

    private static void addLocaleToHttpAcceptLanguage(
            StringBuilder builder, Locale locale) {
        String language = locale.getLanguage();

        if (language != null) {
            builder.append(language);

            String country = locale.getCountry();

            if (country != null) {
                builder.append("-");
                builder.append(country);
            }
        }
    }
}
