package org.sil.bloom.reader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sil.bloom.reader.models.BookOrShelf;

import java.io.File;
import java.util.Locale;

public class BloomShelfFileReader {
    public static BookOrShelf parseShelfFile(String filepath) {
        String backgroundColor = "ffffff"; // default white
        String shelfName = BookOrShelf.getNameFromPath(filepath);
        String shelfId = null;
        String json = IOUtilities.FileToString(new File(filepath));
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
        BookOrShelf shelf = new BookOrShelf(filepath, shelfName);
        shelf.backgroundColor = backgroundColor;
        shelf.shelfId = shelfId;
        return shelf;
    }
}
