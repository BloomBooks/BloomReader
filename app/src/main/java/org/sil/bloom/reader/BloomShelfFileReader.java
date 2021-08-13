package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookOrShelf;

import java.io.File;
import java.util.Locale;

public class BloomShelfFileReader {
    public static BookOrShelf parseShelfFile(String filepath) {
        String json = IOUtilities.FileToString(new File(filepath));
        String shelfName = BookOrShelf.getNameFromPath(filepath);
        return parseShelfJson(filepath, null, json, shelfName);
    }

    public static BookOrShelf parseShelfUri(Context context, Uri uri) {
        String json = IOUtilities.UriToString(context, uri);
        String shelfName = BookOrShelf.getNameFromPath(uri.getPath());
        return parseShelfJson(null, uri, json, shelfName);
    }

    @NonNull
    private static BookOrShelf parseShelfJson(String filepath, Uri uri, String json, String shelfName) {
        String backgroundColor = "ffffff"; // default white
        String shelfId = null;
        try {
            JSONObject data = new JSONObject(json);
            // roughly in priority order, so if anything goes wrong with the json parsing,
            // we get the most important information.
            shelfId = data.getString("id");
            backgroundColor = data.getString("color");
            JSONArray labels = data.getJSONArray("label");
            String uiLang = Locale.getDefault().getLanguage();
            for (int i = 0; i < labels.length(); i++) {
                JSONObject label = labels.getJSONObject(i);
                if (label.has(uiLang)) {
                    shelfName = label.getString(uiLang);
                    break;
                }
                // An English label if any is a better default than the file name, but
                // we will keep looking.
                if (label.has("en")) {
                    shelfName = label.getString("en");
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        BookOrShelf shelf = uri == null ? new BookOrShelf(filepath, shelfName) : new BookOrShelf(uri, shelfName);
        shelf.backgroundColor = backgroundColor;
        shelf.shelfId = shelfId;
        return shelf;
    }
}
