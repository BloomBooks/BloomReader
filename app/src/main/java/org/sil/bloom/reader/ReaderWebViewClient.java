package org.sil.bloom.reader;

import static androidx.core.content.ContextCompat.startActivity;

import androidx.annotation.Nullable;

import android.content.Intent;
import android.net.Uri;
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
        String url = request.getUrl().toString();

        WebResourceResponse fontResponse = getHostedFontResponseIfApplicable(url);
        if (fontResponse != null)
            return fontResponse;

        if (urlIsAllowed(url))
            return super.shouldInterceptRequest(view, request);

        return new WebResourceResponse("text", "utf-8", 403,
                "request for file not part of book",
                new HashMap<String, String>(), new ByteArrayInputStream("".getBytes()));
    }

    // Allow external links to be opened in the default browser. (BL-13801)
    @Override
    public boolean shouldOverrideUrlLoading (WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(view.getContext(), browserIntent, null);
        }
        return true;
    }

    // Starting in bloom-player 2.1, we have font-face rules which tell the host to serve up
    // the appropriate Andika or Andika New Basic font file. An example is:
    //             @font-face {
    //                font-family: "Andika New Basic";
    //                font-weight: bold;
    //                font-style: normal;
    //                src:
    //                    local("Andika New Basic Bold"),
    //                    local("Andika Bold"),
    //    ===>            url("./host/fonts/Andika New Basic Bold"),
    //                    url("https://bloomlibrary.org/fonts/Andika%20New%20Basic/AndikaNewBasic-B.woff")
    //                ;
    //            }
    // So if we have a request for /host/fonts/, here is where we intercept and handle it.
    private WebResourceResponse getHostedFontResponseIfApplicable(String url) {
        try {
            if (!url.contains("/host/fonts/"))
                return null;

            String fontNameRequested = IOUtilities.getFilename(url);
            fontNameRequested = URLDecoder.decode(fontNameRequested, "UTF-8");
            String fontFileName = null;
            switch (fontNameRequested) {
                case "Andika New Basic":
                case "Andika":
                    fontFileName = "Andika-Regular.ttf";
                    break;
                case "Andika New Basic Bold":
                case "Andika Bold":
                    fontFileName = "Andika-Bold.ttf";
                    break;
                case "Andika New Basic Italic":
                case "Andika Italic":
                    fontFileName = "Andika-Italic.ttf";
                    break;
                case "Andika New Basic Bold Italic":
                case "Andika Bold Italic":
                    fontFileName = "Andika-BoldItalic.ttf";
                    break;
            }

            if (fontFileName != null) {
                return new WebResourceResponse("font/ttf", "utf-8", 200,
                        "OK", new HashMap<>(),
                        BloomReaderApplication.getBloomApplicationContext().getAssets().open("fonts/Andika/" + fontFileName));
            }
        } catch (Exception e) {
            // Just silently fail on any exception; definitely not a good reason to crash
            return null;
        }
        return null;
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
