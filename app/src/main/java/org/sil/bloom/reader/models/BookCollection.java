package org.sil.bloom.reader.models;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sil.bloom.reader.BloomFileReader;
import org.sil.bloom.reader.BloomReaderApplication;
import org.sil.bloom.reader.BloomShelfFileReader;
import org.sil.bloom.reader.BuildConfig;
import org.sil.bloom.reader.IOUtilities;
import org.sil.bloom.reader.R;
import org.sil.bloom.reader.ThumbnailCleanup;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BookCollection {
    public static final String THUMBS_DIR = ".thumbs";
    public static final String NO_THUMBS_DIR = "no-thumbs";

    public static final String BOOKSHELF_PREFIX = "bookshelf:";
    // All the books and shelves loaded from the folder on 'disk'.
    private List<BookOrShelf> _booksAndShelves = new ArrayList<BookOrShelf>();
    // The books and folders we are currently displaying.
    private List<BookOrShelf> mFilteredBooksAndShelves = new ArrayList<BookOrShelf>();
    private File mLocalBooksDirectory;
    // The 'filter' is the id stored in a .bloomshelf file, which (for a book to pass) must match
    // the content of a bookshelf: tag in the book's meta.json (except when null or empty, in
    // which case a book passes if it doesn't match the id of any bookshelf we have).
    private String mFilter = null;
    // The set of shelf ids for the shelves we actually have. Books with none of these pass
    // the empty filter.
    private Set<String> mShelfIds = new HashSet<String>();

    public void setFilter(String filter) {
        mFilter = filter;
        updateFilteredList();
    }

    public int indexOf(BookOrShelf book) { return mFilteredBooksAndShelves.indexOf(book); }

    public BookOrShelf get(int i) {
        return mFilteredBooksAndShelves.get(i);
    }

    public int size() {
        return mFilteredBooksAndShelves.size();
    }

    public BookOrShelf addBookIfNeeded(String path) {
        BookOrShelf existingBook = getBookOrShelfByPath(path);
        if (existingBook != null)
            return existingBook;
        return addBook(path, true);
    }

    // Add a book to the main collection. This is used in two ways: LoadFromDirectory loads
    // them all, then updates mFilteredBooksAndShelves to the appropriate set for the current
    // filter and sorts it.
    // Various other callers add a single book. So far all of these want it to be visible
    // at once, even if it doesn't really belong in the current filter. Thus, passing addingJustOne
    // true causes the book to be unconditionally added to mFilteredBooksAndShelves, and it gets
    // re-sorted.
    private BookOrShelf addBook(String path, boolean addingJustOne) {
        BookOrShelf bookOrShelf;
        if (path.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION)) {
            bookOrShelf = BloomShelfFileReader.parseShelfFile(path);
            if (bookOrShelf.shelfId != null)
                mShelfIds.add(bookOrShelf.shelfId);
        } else {
            // book.
            bookOrShelf = new BookOrShelf(path);
        }
        BookCollection.setShelvesOfBook(bookOrShelf);
        _booksAndShelves.add(bookOrShelf);
        if (addingJustOne) {
            mFilteredBooksAndShelves.add(bookOrShelf);
            Collections.sort(mFilteredBooksAndShelves, BookOrShelf.AlphabeticalComparator);
        }
        return bookOrShelf;
    }

    public BookOrShelf getBookOrShelfByPath(String path) {
        for (BookOrShelf bookOrShelf: _booksAndShelves) {
            if (bookOrShelf.path.equals(path))
                return bookOrShelf;
        }
        return null;
    }

    public List<BookOrShelf> getAllBooksWithinShelf(BookOrShelf targetShelf){
        ArrayList<BookOrShelf> booksAndShelves = new ArrayList<>();
        booksAndShelves.add(targetShelf);
        for(BookOrShelf bookOrShelf : _booksAndShelves){
            if (isBookInFilter(bookOrShelf, targetShelf.shelfId, mShelfIds)) {
                booksAndShelves.add(bookOrShelf);
                if (bookOrShelf.isShelf())
                    booksAndShelves.addAll(getAllBooksWithinShelf(bookOrShelf));
            }
        }
        return booksAndShelves;
    }

    public void init(Context context) throws ExtStorageUnavailableException {
        File[] booksDirs = getLocalAndRemovableBooksDirectories(context);
        mLocalBooksDirectory = booksDirs[0];
        if (BloomReaderApplication.isFirstRunAfterInstallOrUpdate()){
            SampleBookLoader.CopySampleBooksFromAssetsIntoBooksFolder(context, mLocalBooksDirectory);
        }
        loadFromDirectories(booksDirs);
    }

    public static File getLocalBooksDirectory() throws ExtStorageUnavailableException {
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            throw new ExtStorageUnavailableException();
        File bloomDir = Environment.getExternalStoragePublicDirectory("Bloom");
        bloomDir.mkdirs();
        return bloomDir;
    }

    public static File[] getLocalAndRemovableBooksDirectories(Context context) throws ExtStorageUnavailableException {
        File localBooksDir = getLocalBooksDirectory();
        File remoteStorageDir = IOUtilities.removablePublicStorageRoot(context);
        File remoteBooksDir = new File(remoteStorageDir, "BloomExternal");
        if (remoteBooksDir.exists() && !remoteBooksDir.equals(localBooksDir))
            return new File[] {localBooksDir, remoteBooksDir};
        return new File[] {localBooksDir};
    }

    private void loadFromDirectories(File[] booksDirs) {
        mShelfIds.clear();
        _booksAndShelves.clear();
        for (File booksDir : booksDirs)
            loadFromDirectory(booksDir);
    }

    private void loadFromDirectory(File directory) {
        File[] files = directory.listFiles();
        if(files != null) {
            for (int i = 0; i < files.length; i++) {
                final String name = files[i].getName();
                if (!name.endsWith(IOUtilities.BOOK_FILE_EXTENSION)
                        && !name.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION))
                    continue; // not a book (nor a shelf)!
                final String path = files[i].getAbsolutePath();
                if (name.endsWith(IOUtilities.BOOK_FILE_EXTENSION) &&
                        !IOUtilities.isValidZipFile(new File(path), IOUtilities.CHECK_BLOOMD)) {
                    String markedName = name + "-BAD";
                    Log.w("BloomCollection", "Renaming invalid book file "+path+" to "+markedName);
                    Context context = BloomReaderApplication.getBloomApplicationContext();
                    String message = context.getString(R.string.renaming_invalid_book, markedName);
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                    new File(path).renameTo(new File(path+"-BAD"));
                    continue;
                }
                addBook(path, false);
            }
            updateFilteredList();
        }
    }

    private void updateFilteredList() {
        mFilteredBooksAndShelves.clear();
        for (BookOrShelf bookOrShelf: _booksAndShelves) {
            if (isBookInFilter(bookOrShelf, mFilter, mShelfIds)) {
                mFilteredBooksAndShelves.add(bookOrShelf);
            }
        }
        Collections.sort(mFilteredBooksAndShelves, BookOrShelf.AlphabeticalComparator);
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

    public List<BookOrShelf> getBooks() {
        return mFilteredBooksAndShelves;
    }

    public void deleteFromDevice(BookOrShelf book) {
        if (book == null)
            return;
        if (book.path != null) {
            File file = new File(book.path);
            if (file.exists()) {
                file.delete();
            }
        }
        _booksAndShelves.remove(book);
        mFilteredBooksAndShelves.remove(book);
    }

    // is this coming from somewhere other than where we store books?
    // then move or copy it in
    public String ensureBookIsInCollection(Context context, Uri bookUri, String filename) {
        if (bookUri.getPath().contains(mLocalBooksDirectory.getAbsolutePath()))
            return bookUri.getPath();

        Log.d("BloomReader", "Copying book into Bloom directory");
        String destination = mLocalBooksDirectory.getAbsolutePath() + File.separator + filename;
        if (filename.endsWith(IOUtilities.BOOK_FILE_EXTENSION + IOUtilities.ENCODED_FILE_EXTENSION)) {
            destination = destination.substring(0, destination.length() - IOUtilities.ENCODED_FILE_EXTENSION.length());
        }
        boolean copied = IOUtilities.copyBloomdFile(context, bookUri, destination);
        if(copied){
            // it's probably not in our list that we display yet, so make an entry there
            addBookIfNeeded(destination);
            return destination;
        } else{
            return null;
        }
    }

    // Tests whether a book passes the current 'filter'.
    // A null (or empty) filter, used by the main activity, contains books that are not on any
    // (existing) shelf. A non-empty filter, which is expected to be the ID of a shelf, contains books
    // which are on that shelf. (existingShelves passes in the list of shelves that actually
    // exist as files in the folder. a book tagged as being on a nonexistent shelf can still be
    // in the root collection.)
    public static boolean isBookInFilter(BookOrShelf book, String filter, Set<String> existingShelves) {
        if (filter == null || filter.length() == 0)
            return !book.isBookInAnyShelf(existingShelves);
        else {
            return book.isBookInShelf(filter);
        }
    }

    // Set the shelves if any that a book or shelf belongs to.
    // Extracts the meta.json entry from the bloomd file, extracts the tags from that,
    // finds any that start with "bookshelf:", and sets the balance of the tag as one of the
    // book's shelves.
    public static void setShelvesOfBook(BookOrShelf bookOrShelf) {
        String json;
        try {
            if (bookOrShelf.isShelf()) {
                json = IOUtilities.FileToString(new File(bookOrShelf.path));
            }
            else {
                byte[] jsonBytes = IOUtilities.ExtractZipEntry(new File(bookOrShelf.path), "meta.json");
                json = new String(jsonBytes, "UTF-8");
            }
            JSONObject data = new JSONObject(json);
            JSONArray tags = data.getJSONArray("tags");
            for (int i = 0; i < tags.length(); i++) {
                String tag = tags.getString(i);
                if (!tag.startsWith(BOOKSHELF_PREFIX))
                    continue;
                bookOrShelf.addBookshelf(tag.substring(BOOKSHELF_PREFIX.length()).trim());
            }
            if (data.has("brandingProjectName")) {
                bookOrShelf.brandingProjectName = data.getString("brandingProjectName");
            }
        } catch (Exception e) {
            // Not sure about just catching everything like this. But the worst that happens if
            // a bloomd does not contain valid meta.json from which we can extract tags is that
            // the book shows up at the root instead of on a shelf.
            e.printStackTrace();
        }
    }

    public static Uri getThumbnail(Context context, BookOrShelf book){
        try {
            File thumbsDirectory = getThumbsDirectory();
            String thumbPath = thumbsDirectory.getPath() + File.separator + book.name;
            File thumb = new File(thumbPath);
            if (thumb.exists()) {
                if (thumb.lastModified() < new File(book.path).lastModified()) {
                    thumb.delete();
                    return new BloomFileReader(context, book.path).getThumbnail(thumbsDirectory);
                }
                return Uri.fromFile(thumb);
            }

            File noThumb = new File(thumbsDirectory.getPath() + File.separator + NO_THUMBS_DIR + File.separator + book.name);
            if (noThumb.exists()){
                if (noThumb.lastModified() < new File(book.path).lastModified()) {
                    noThumb.delete();
                    return new BloomFileReader(context, book.path).getThumbnail(thumbsDirectory);
                }
                return null;
            }

            return new BloomFileReader(context, book.path).getThumbnail(thumbsDirectory);
        }
        catch (IOException e){
            Log.e("BookCollection", "IOException getting thumbnail: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static File getThumbsDirectory() throws IOException {
        String bloomDirectoryPath = getLocalBooksDirectory().getPath();
        String thumbsDirectoryPath = bloomDirectoryPath + File.separator + THUMBS_DIR;
        File thumbsDirectory = new File(thumbsDirectoryPath);
        if(!thumbsDirectory.exists()){
            thumbsDirectory.mkdir();
            File noMedia = new File(thumbsDirectoryPath + File.separator + ".nomedia");
            noMedia.createNewFile();
            File noThumb = new File(thumbsDirectoryPath + File.separator + NO_THUMBS_DIR);
            noThumb.mkdir();
        }
        return thumbsDirectory;
    }

    public static void cleanUpOldThumbs(Context context){
        try {
            new ThumbnailCleanup(context).execute(getThumbsDirectory());
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}
