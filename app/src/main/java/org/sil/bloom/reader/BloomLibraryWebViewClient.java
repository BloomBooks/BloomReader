package org.sil.bloom.reader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient;

// This class is used by BloomLibraryActivity to configure its WebView. It passes on the page title
// when the page is loaded and overrides another method to prevent the system from loading certain
// URLs in the default system browser instead of within the webview.
public class BloomLibraryWebViewClient extends WebViewClient {

    Activity mOwner;
    public BloomLibraryWebViewClient(Activity owner) {
        mOwner = owner;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // Note, the webview never sees links when they are handled by the blorg router.

        final Uri uri = request.getUrl();
        if (String.valueOf(uri).contains("app-hosted-")) {
            // load this url in the webView itself
            return false;
        } else {
            // Open in external browser if not an app-hosted link (e.g. normal book detail page)
            final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            mOwner.startActivity(intent);
            return true;
        }
    }

    class Response {
        byte[] content;
        String contentType;
        String contentEncoding;
    }
    static Dictionary<String, Response> sCache = new Hashtable<String, Response>();

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // The commented-out code is a first attempt at caching results so that we only ever get each once.
        // It works well for bloom-library's own resources but not for parse queries.
        // It gets the data from parse, but attempting to return it to the browser fails with
        // no useful message, in the network tab it looks as if the response just never arrived.
        // Note that for some reason we're getting nulls from connection.getEncoding(), and strings
        // like "application-json; charset utf-8" which seem to contain both type and encoding
        // from connection.getContentType(). One theory I was about to explore was that separating
        // these bits of information and passing them correctly to the web response would help.
        // Another idea is that I'm not getting the CORS headers right in my constructed response;
        // but in either of these cases I'd expect the browser to report that it got something!
        // So it remains a mystery.
        // Assuming we decide to try caching again, and that the above problems can be solved,
        // so it's working as an in-memory cache, the next step would be to save the cache data
        // somewhere more persistent (there's something like context.getCache() that returns a good
        // place for cache-type data that the OS can delete if it needs space).
        // And then we have to think about how long to keep it and whether some or all queries
        // should be redone in the background while we return the cached data. For example, we might
        // often update the root HTML document, which allows the whole website to be updated since
        // it uses new hashed names for everything else. Parse queries should be updated often
        // enough to see new information. Conceivably we could implement some mechanism to update
        // the UI if a background query produces different data.
        // Note that some of the same effects could be produced, perhaps with somewhat less control,
        // using a service worker on the web side of things.
//        Uri uri = request.getUrl();
//        String path = uri.getPath();
//        String url = uri.toString();
//        Response data = sCache.get(url);
//        if (data == null) {
//            try {
//                data = new Response();
//                data.content = new byte[0];
//                URL url1 = new java.net.URL(url);
//                HttpURLConnection connection = (HttpURLConnection)url1.openConnection();
//
//                if (path.startsWith("/parse/")) {
//                    connection.setRequestProperty("X-Parse-Application-Id", "R6qNTeumQXjJCMutAJYAwPtip1qBulkFyLefkCE5");
//                } else {
//                    for (Map.Entry<String, String> kvp:request.getRequestHeaders().entrySet())
//                    {
//                        connection.addRequestProperty(kvp.getKey(), kvp.getValue());
//                    }
//                }
//                InputStream in = connection.getInputStream();
//
//                data.contentEncoding = connection.getContentEncoding();
//                data.content = IOUtils.toByteArray(in);
//                if (data.content == null) {
//                    data.content = new byte[0];
//                }
//                data.contentType = connection.getContentType();;
//                sCache.put(url, data);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//        InputStream result = new ByteArrayInputStream(data.content);
//        WebResourceResponse response = new WebResourceResponse(data.contentType, data.contentEncoding, result);
//        Map<String, String> headers = new HashMap<String, String>();
//        headers.put("Access-Control-Allow-Origin", "*");
//        response.setResponseHeaders(headers);
//        return response;
        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        mOwner.setTitle(view.getTitle());
    }
}