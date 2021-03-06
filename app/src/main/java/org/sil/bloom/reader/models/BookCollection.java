package org.sil.bloom.reader.models;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sil.bloom.reader.BloomFileReader;
import org.sil.bloom.reader.BloomReaderApplication;
import org.sil.bloom.reader.BloomShelfFileReader;
import org.sil.bloom.reader.TextFileContent;
import org.sil.bloom.reader.IOUtilities;
import org.sil.bloom.reader.InitializeLibraryTask;
import org.sil.bloom.reader.R;
import org.sil.bloom.reader.ThumbnailCleanup;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class BookCollection {
    public static final String THUMBS_DIR = ".thumbs";
    public static final String NO_THUMBS_DIR = "no-thumbs";

    public static final String BOOKSHELF_PREFIX = "bookshelf:";
    // All the books and shelves loaded from the folder on 'disk'.
    // CopyOnWriteArrayList allows thread-safe unsychronized access.
    private List<BookOrShelf> _booksAndShelves = new CopyOnWriteArrayList<BookOrShelf>();
    // The books and folders we are currently displaying. As this is accessed from multiple
    // threads and is not thread-safe, all methods using it directly should be synchronized.
    // To minimise blocking threads, typically time-consuming modifications are performed
    // by getting a clone with getCopyOfFilteredBooksAndShelves() or just starting over with
    // an empty collection, then using replaceFilteredBooksAndShelves() to replace it atomically.
    // (Not using CopyOnWriteArrayList here as we need to sort it and I doubt this can be done
    // efficiently to a CopyOnWriteArrayList.)
    private List<BookOrShelf> mFilteredBooksAndShelves = new ArrayList<BookOrShelf>();
    private File mLocalBooksDirectory;
    // The 'filter' is the id stored in a .bloomshelf file, which (for a book to pass) must match
    // the content of a bookshelf: tag in the book's meta.json (except when null or empty, in
    // which case a book passes if it doesn't match the id of any bookshelf we have).
    private String mFilter = null;
    // The set of shelf ids for the shelves we actually have. Books with none of these pass
    // the empty filter.
    private Set<String> mShelfIds = new HashSet<String>();

    private InitializeLibraryTask mInitializeTask = null;

    public void setFilter(String filter) {
        mFilter = filter;
        updateFilteredList();
    }

    public synchronized int indexOf(BookOrShelf book) { return mFilteredBooksAndShelves.indexOf(book); }

    public synchronized BookOrShelf get(int i) {
        return mFilteredBooksAndShelves.get(i);
    }

    public synchronized int size() {
        return mFilteredBooksAndShelves.size();
    }

    public BookOrShelf addBookIfNeeded(String path) {
        BookOrShelf existingBook = getBookOrShelfByPath(path);
        if (existingBook != null)
            return existingBook;
        return addBook(path, null);
    }

    private BookOrShelf makeBookOrShelf(String path, TextFileContent metaFile) {
        BookOrShelf bookOrShelf;
        if (path.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION)) {
            bookOrShelf = BloomShelfFileReader.parseShelfFile(path);
            if (bookOrShelf.shelfId != null)
                mShelfIds.add(bookOrShelf.shelfId);
        } else {
            // book.
            bookOrShelf = new BookOrShelf(path);
        }
        BookCollection.setShelvesAndTitleOfBook(bookOrShelf, metaFile);
        return bookOrShelf;
    }

    // Add a book to the main collection. If adding many, it is better to add them all at once
    // with addBooks(), which updates mFilteredBooksAndShelves just once.
    // Callers of this add a single book. So far all of these want it to be visible
    // at once, even if it doesn't really belong in the current filter. So, the book is
    // unconditionally added to mFilteredBooksAndShelves, and it gets re-sorted at once.
    private BookOrShelf addBook(String path, TextFileContent metaFile) {
        BookOrShelf bookOrShelf = makeBookOrShelf(path, metaFile);
        _booksAndShelves.add(bookOrShelf);
        // This process of copying the collection is probably unnecessary here,
        // but is done wherever it is modified for thread safety. This way,
        // no other thread ever accesses it while in an invalid state.
        ArrayList<BookOrShelf> newList = getCopyOfFilteredBooksAndShelves();
        newList.add(bookOrShelf);
        Collections.sort(newList, BookOrShelf.AlphanumComparator);
        replaceFilteredBooksAndShelves(newList);
        return bookOrShelf;
    }

    private synchronized ArrayList<BookOrShelf> getCopyOfFilteredBooksAndShelves() {
        return  new ArrayList<BookOrShelf>(mFilteredBooksAndShelves);
    }

    private synchronized void replaceFilteredBooksAndShelves(ArrayList<BookOrShelf> newList) {
        mFilteredBooksAndShelves = newList;
    }

    private void addBooks(ArrayList<BookOrShelf> books) {
        _booksAndShelves.addAll(books);
        updateFilteredList();
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

    public void init(Activity activity, InitializeLibraryTask task) throws ExtStorageUnavailableException {
        Context context = activity.getApplicationContext();
        File[] booksDirs = getLocalAndRemovableBooksDirectories(context);
        mLocalBooksDirectory = booksDirs[0];
        mInitializeTask = task;
        if (BloomReaderApplication.isFirstRunAfterInstallOrUpdate()){
            SampleBookLoader.CopySampleBooksFromAssetsIntoBooksFolder(context, mLocalBooksDirectory);
        }
        loadFromDirectories(booksDirs, activity);
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

    private void loadFromDirectories(File[] booksDirs, Activity activity) {
        mShelfIds.clear();
        _booksAndShelves.clear();
        if (mInitializeTask != null) {
            Integer count = 0;
            for (File booksDir : booksDirs) {
                count += booksDir.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.endsWith(IOUtilities.BOOK_FILE_EXTENSION) || name.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION);
                    }
                }).length;
            }
            mInitializeTask.setBookCount(count);
        }
        for (File booksDir : booksDirs)
            loadFromDirectory(booksDir, activity);
    }

    private void loadFromDirectory(File directory, Activity activity) {
        File[] files = directory.listFiles();
        ArrayList<BookOrShelf> books = new ArrayList<BookOrShelf>();
        if(files != null) {
            for (int i = 0; i < files.length; i++) {
                final String name = files[i].getName();
                TextFileContent metaFile = new TextFileContent("meta.json");
                if (!name.endsWith(IOUtilities.BOOK_FILE_EXTENSION)
                        && !name.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION))
                    continue; // not a book (nor a shelf)!
                final String path = files[i].getAbsolutePath();
                if (name.endsWith(IOUtilities.BOOK_FILE_EXTENSION) &&
                        !IOUtilities.isValidZipFile(new File(path), IOUtilities.CHECK_BLOOMD, metaFile)) {
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            String markedName = name + "-BAD";
                            Log.w("BloomCollection", "Renaming invalid book file "+path+" to "+markedName);
                            Context context = BloomReaderApplication.getBloomApplicationContext();
                            String message = context.getString(R.string.renaming_invalid_book, markedName);
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                        }
                    });
                    new File(path).renameTo(new File(path+"-BAD"));
                    if (mInitializeTask != null) {
                        mInitializeTask.incrementBookProgress();
                    }
                    continue;
                }
                books.add(makeBookOrShelf(path, metaFile));
                if (mInitializeTask != null) {
                    mInitializeTask.incrementBookProgress();
                }
            }
            addBooks(books);
        }
    }

    private void updateFilteredList() {
        ArrayList<BookOrShelf> newList = new ArrayList<BookOrShelf>();
        for (BookOrShelf bookOrShelf: _booksAndShelves) {
            if (isBookInFilter(bookOrShelf, mFilter, mShelfIds)) {
                newList.add(bookOrShelf);
            }
        }
        Collections.sort(newList, BookOrShelf.AlphanumComparator);
        // This atomic update guards against any other thread accessing the collection in an
        // incomplete state, while also preventing any delays from long locks.
        replaceFilteredBooksAndShelves(newList);
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

    public synchronized void deleteFromDevice(BookOrShelf book) {
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
    public String ensureBookIsInCollection(Context context, Uri bookUri, String filename) throws ExtStorageUnavailableException {
        if (bookUri == null || bookUri.getPath() == null)
            return null; // Play console proves this is possible somehow

        // Possible for this to happen if we load a .bloomd directly without loading the whole app first (BL-8218)
        if (mLocalBooksDirectory == null)
            mLocalBooksDirectory = getLocalBooksDirectory();

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
    // book's shelves.  The meta.json data may or may not have already been extracted.
    public static void setShelvesAndTitleOfBook(BookOrShelf bookOrShelf, TextFileContent metaFile) {
        String json;
        try {
            if (bookOrShelf.isShelf()) {
                json = IOUtilities.FileToString(new File(bookOrShelf.path));
            }
            else {
                if (metaFile != null && metaFile.Content != null && !metaFile.Content.isEmpty()) {
                    json = metaFile.Content;
                } else {
                    byte[] jsonBytes = IOUtilities.ExtractZipEntry(new File(bookOrShelf.path), "meta.json");
                    json = new String(jsonBytes, "UTF-8");
                }
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
            if (data.has("title")) {
                bookOrShelf.title = data.getString("title");
            }
        } catch (Exception e) {
            // Not sure about just catching everything like this. But the worst that happens if
            // a bloomd does not contain valid meta.json from which we can extract tags is that
            // the book shows up at the root instead of on a shelf, and that its title comes from
            // the filename, which is based on the title and usually correct.
            e.printStackTrace();
        }
    }

    // The second argument is really optional.
    public static void setShelvesAndTitleOfBook(BookOrShelf bookOrShelf) {
        setShelvesAndTitleOfBook(bookOrShelf, null);
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
