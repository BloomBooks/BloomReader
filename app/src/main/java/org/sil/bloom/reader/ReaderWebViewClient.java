package org.sil.bloom.reader;

import androidx.annotation.Nullable;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;

// This class improves security. Our WebView is, at the level of its basic settings, allowed
// extensive file system access. However, all the requests for anything come through this
// class's shouldInterceptRequest method. We forbid any access to any url that isn't part
// of the folder where we decompressed this book. (Files that are in the app's assets folder,
// like our question sounds, are exempt from this check.)
public class ReaderWebViewClient extends WebViewClient {

    String mAllowedPathPrefix;
    BloomFileReader mFileReader;
    int mLengthOfCanonicalPrefix;
    public ReaderWebViewClient(String bookFolderPath, BloomFileReader fileReader) {
        // Our basic strategy is to extract files to a directory and give bloom-player
        // a file:// url to the root html directory in that folder. So any valid urls
        // will start with file:// plus the path to the folder.
        mAllowedPathPrefix = "file://" + bookFolderPath;
        mFileReader = fileReader;
        // To get a key for fileReader.tryGetFile, we need to take the canonical path
        // of the file requested and strip off the bit indicated by allowedPathPrefix.
        // (plus one more slash).
        mLengthOfCanonicalPrefix = bookFolderPath.length() + 1;
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
        String path = null;
        try {
            path = URLDecoder.decode(url.substring("file://".length()), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(); // absolutely stupid compiler requirement, of course UTF-8 is supported!
        }
        try {
            String canonicalPath = new File(path).getCanonicalPath();
            String canonicalUrl = "file://" + canonicalPath;
            if (canonicalUrl.startsWith(mAllowedPathPrefix)) {
                // Make sure the file we want has been unzipped.
                String keyInZip = canonicalPath.substring(mLengthOfCanonicalPrefix);
                int index = keyInZip.indexOf("?");
                if (index >= 0) {
                    keyInZip = keyInZip.substring(0,index);
                }
                mFileReader.tryGetFile(keyInZip);
                return true;
            }
            // I think this only happens before Android 21 (Lollipop); in later androids,
            // the app's own assets are automatically OK.
            return canonicalUrl.startsWith("file:///android_asset/bloom-player/");
        } catch (IOException e) {
            return false;
        }
    }
}
