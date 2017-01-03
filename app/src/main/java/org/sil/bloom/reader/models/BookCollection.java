package org.sil.bloom.reader.models;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Environment;
import android.widget.Toast;

import org.sil.bloom.reader.IOUtilities;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookCollection {

    private List<Book> _books = new ArrayList<Book>();
    private final Map<String, Book> ID_TO_BOOK_MAP = new HashMap<String, Book>();

    public Book get(int i) {
        return _books.get(i);
    }

    public int size() {
        return _books.size();
    }

    private void addBook(Book item) {
        _books.add(item);
        ID_TO_BOOK_MAP.put(item.id, item);
    }

    public void init(Context context) {
        ContextWrapper cw = new ContextWrapper(context);
        File directory = cw.getExternalFilesDir("books");
        IOUtilities.deleteFileOrDirectory(directory);

        SampleBookLoader.CopySampleBooksFromAssetsIntoBooksFolder(context, directory);

//        for (int i = 2; i <= 4; i++) {
//            createFilesForDummyBook(context, directory, i);
//        }

        // load from our private directory. We may get rid of this entirely
        LoadFromDirectory(directory);

        // load from the directory the user can see
        LoadFromDirectory(Environment.getExternalStoragePublicDirectory("Bloom"));
    }

    private void LoadFromDirectory(File directory) {
        File[] files = directory.listFiles();
        if(files != null) {
            for (int i = 0; i < files.length; i++) {
                String path = files[i].getAbsolutePath();
                String name = files[i].getName();
                addBook(new Book(String.valueOf(i), name, path));
            }
        }
    }

    private void createFilesForDummyBook(Context context, File directory, int position) {
        File bookDir = new File(directory, "The " + position + " dwarves");
        bookDir.mkdir();
        File file = new File(bookDir, "The " + position + " dwarves.htm");
        if (file.exists()) {
            file.delete();
        }

        try {
            FileWriter out = new FileWriter(file);
            out.write("<html><body>Once upon a time, " + position + " dwarves lived in a forest.</body></html>");
            out.close();
        } catch (IOException e) {
            IOUtilities.showError(context, e.getMessage());
        }
    }

}
