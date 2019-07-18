package org.sil.bloom.reader;

import android.os.Build;
import android.support.annotation.Nullable;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.util.HashMap;
import java.util.Map;

// This class improves security. Our WebView is, at the level of its basic settings, allowed
// extensive file system access. However, all the requests for anything come through this
// class's shouldInterceptRequest method. We forbid any access to any url that isn't part
// of the folder where we decompressed this book. (Files that are in the app's assets folder,
// like our question sounds, are exempt from this check.)
public class ReaderWebViewClient extends WebViewClient {

    String mAllowedPathPrefix;
    public ReaderWebViewClient(String allowedPathPrefix) {
        mAllowedPathPrefix = allowedPathPrefix;
    }

    @Nullable
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (urlIsAllowed(request.getUrl().toString()))
                return super.shouldInterceptRequest(view, request);

            return new WebResourceResponse("text", "utf-8", 403,
                    "request for file not part of book",
                    new HashMap<String,String>(), new ByteArrayInputStream("".getBytes()));
    }

    // We use the canonical path of the file to prevent hacks involving a valid directory
    // prefix followed by multiple "../" to get back to one that is not permitted.
    private boolean urlIsAllowed(String url) {
        if (!url.startsWith("file://"))
            return false;
        String path = url.substring("file://".length());
        try {
            String canonicalPath = new File(path).getCanonicalPath();
            String canonicalUrl = "file://" + canonicalPath;
            if (canonicalUrl.startsWith(mAllowedPathPrefix))
                return true;
            // I think this only happens before Android 21 (Lollipop); in later androids,
            // the apps own assets are automatically OK.
            return canonicalUrl.startsWith("file:///android_asset/bloom-player/");
        } catch (IOException e) {
            return false;
        }
    }
}
