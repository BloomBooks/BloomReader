package org.sil.bloom.reader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


// Allows us to supply a resource (e.g. css or javascript) from our assets that a book requested



public class Browser extends WebView {

    public Browser(Context context) {
        this(context, null);
    }

    public Browser(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Browser(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        /* any initialisation work here */
    }

    // This didn't work; seems you can only intercept non-local file requests.
    // We'll see what we can do when we switch to using the crosswalk embedded browser.
    // Code from //ref http://stackoverflow.com/questions/8273991/webview-shouldinterceptrequest-example/8274881#8274881
  /*  public WebResourceResponse shouldInterceptRequest(final WebView view, String url) {
        //Log.i(TAG2, "SHOULD OVERRIDE INIT");
        //String url = webResourceRequest.getUrl().toString();
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        //I have some folders for files with the same extension
        if (extension.equals("css") || extension.equals("js") || extension.equals("img")) {
            return loadFilesFromAssetFolder("book support files", url);
        }
        //more possible extensions for font folder
        if (extension.equals("woff") || extension.equals("woff2") || extension.equals("ttf") || extension.equals("svg") || extension.equals("eot")) {
            return loadFilesFromAssetFolder("font", url);
        }

        return null;
    }

    //get list of files of specific asset folder
    private ArrayList listAssetFiles(String path) {

        List myArrayList = new ArrayList();
        String [] list;
        try {
            list = this.getContext().getAssets().list(path);
            for(String f1 : list){
                myArrayList.add(f1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (ArrayList) myArrayList;
    }

    //get mime type by url
    public String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            if (extension.equals("js")) {
                return "text/javascript";
            }
            else if (extension.equals("woff")) {
                return "application/font-woff";
            }
            else if (extension.equals("woff2")) {
                return "application/font-woff2";
            }
            else if (extension.equals("ttf")) {
                return "application/x-font-ttf";
            }
            else if (extension.equals("eot")) {
                return "application/vnd.ms-fontobject";
            }
            else if (extension.equals("svg")) {
                return "image/svg+xml";
            }
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }

    public WebResourceResponse loadFilesFromAssetFolder (String folder, String url) {
        List myArrayList = listAssetFiles(folder);
        AssetManager assets = this.getContext().getAssets();
        for (Object str : myArrayList) {
            if (url.contains((CharSequence) str)) {
                try {
                    return new WebResourceResponse(getMimeType(url), "UTF-8", assets.open(String.valueOf(folder+"/" + str)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
    */
}
