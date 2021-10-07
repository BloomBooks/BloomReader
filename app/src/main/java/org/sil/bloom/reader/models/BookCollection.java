package org.sil.bloom.reader.models;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sil.bloom.reader.BaseActivity;
import org.sil.bloom.reader.BloomFileReader;
import org.sil.bloom.reader.BloomReaderApplication;
import org.sil.bloom.reader.BloomShelfFileReader;
import org.sil.bloom.reader.BookSearchListener;
import org.sil.bloom.reader.SAFUtilities;
import org.sil.bloom.reader.TextFileContent;
import org.sil.bloom.reader.IOUtilities;
import org.sil.bloom.reader.InitializeLibraryTask;
import org.sil.bloom.reader.R;
import org.sil.bloom.reader.ThumbnailCleanup;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class BookCollection {
    public static final String THUMBS_DIR = ".thumbs";
    public static final String NO_THUMBS_DIR = "no-thumbs";

    public static final String BOOKSHELF_PREFIX = "bookshelf:";
    // All the books and shelves loaded from the folder on 'disk'.
    // CopyOnWriteArrayList allows thread-safe unsynchronized access.
    private final List<BookOrShelf> _booksAndShelves = new CopyOnWriteArrayList<BookOrShelf>();
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
    private final Set<String> mShelfIds = new HashSet<String>();

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

    public BookOrShelf addBookOrShelfIfNeeded(String pathOrUri) {
 		pathOrUri = fixBloomd(pathOrUri);
        BookOrShelf existingBook = getBookOrShelfByPath(pathOrUri);
        if (existingBook != null)
            return existingBook;
        return addBookOrShelf(pathOrUri, null);
    }

    private BookOrShelf makeBookOrShelf(String pathOrUri, TextFileContent metaFile) {
        BookOrShelf bookOrShelf;
        if (pathOrUri.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION)) {
            bookOrShelf = BloomShelfFileReader.parseShelfFile(pathOrUri);
            if (bookOrShelf.shelfId != null)
                mShelfIds.add(bookOrShelf.shelfId);
        } else {
            // book.
            bookOrShelf = new BookOrShelf(pathOrUri);
        }
        BookCollection.setShelvesAndTitleOfBook(bookOrShelf, metaFile);
        return bookOrShelf;
    }

    private BookOrShelf makeBookOrShelf(Uri uri, TextFileContent metaFile) {
        BookOrShelf bookOrShelf;
        String path = uri.getPath();
        if (path.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION)) {
            bookOrShelf = BloomShelfFileReader.parseShelfUri(BloomReaderApplication.getBloomApplicationContext(), uri);
            if (bookOrShelf.shelfId != null)
                mShelfIds.add(bookOrShelf.shelfId);
        } else {
            // book.
            bookOrShelf = new BookOrShelf(uri);
        }
        BookCollection.setShelvesAndTitleOfBook(bookOrShelf, metaFile);
        return bookOrShelf;
    }

    // Add a book to the main collection. If adding many, it is better to add them all at once
    // with addBooks(), which updates mFilteredBooksAndShelves just once.
    // Callers of this add a single book. So far all of these want it to be visible
    // at once, even if it doesn't really belong in the current filter. So, the book is
    // unconditionally added to mFilteredBooksAndShelves, and it gets re-sorted at once.
    private BookOrShelf addBookOrShelf(String pathOrUrl, TextFileContent metaFile) {
        BookOrShelf bookOrShelf = makeBookOrShelf(pathOrUrl, metaFile);
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

    private void addBooks(List<BookOrShelf> books) {
        _booksAndShelves.addAll(books);
        updateFilteredList();
    }

    public BookOrShelf getBookOrShelfByPath(String path) {
        for (BookOrShelf bookOrShelf: _booksAndShelves) {
            if (bookOrShelf.pathOrUri.equals(path))
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

    public void init(Activity activity, InitializeLibraryTask task) {
        Context context = activity.getApplicationContext();
        File[] booksDirs = getLocalAndRemovableBooksDirectories(context);
        mLocalBooksDirectory = booksDirs[0];
        mInitializeTask = task;
        if (BloomReaderApplication.isFirstRunAfterInstallOrUpdate()){
            SampleBookLoader.CopySampleBooksFromAssetsIntoBooksFolder(context, mLocalBooksDirectory);
        }
        loadFromDirectories(booksDirs, activity);
    }

    // This is currently the folder where Bloom stores all its data. A couple of things might make
    // sense to move up to the root filesDir.
    public static File getLocalBooksDirectory() {
        File bloomDir = new File(BloomReaderApplication.getBloomApplicationContext().getFilesDir(), "books");
        bloomDir.mkdirs();
        return bloomDir;
    }

//    private static boolean isExternalStorageReadable() {
//        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED ||
//                Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED_READ_ONLY;
//    }

    // Return a path to our Books folder on the SD card, or null if there isn't one.
    // Note that this is channel dependent. In Device File Explorer, the folder will be in the
    // SD card device (see storage/<some 8-letter code>/Android/data/org.sil.bloom.reader...
    // Running BR once will create this folder, whose name is specific to your channel...
    // for example, org.sil.bloom.reader.alpha.debug, and under it, the files directory, which is
    // the bit of the SD card you can access without permission. Inside that, Bloom looks to see
    // whether a Books folder exists, and if so, returns it.
    private static File privateSdCardBooksDir(Context context) {
        // It is tempting here to simply pass "Books" as the argument to getExternalFiles, rather
        // than getting the root folder and then appending /Books. However, getExternalFiles("Books")
        // will create the Books folder if it doesn't already exist. Our goal is to find out whether
        // it already exists.
        File[] sdCardPrivateDirs = context.getExternalFilesDirs("");
        if (sdCardPrivateDirs.length > 1) {
            File booksDir = new File(sdCardPrivateDirs[1].getPath() + "/Books");
            if (booksDir.exists())
                return booksDir;
        }
        return null;
    }

    public static File[] getLocalAndRemovableBooksDirectories(Context context) {
        ArrayList<File> dirs = new ArrayList<>();
        File localBooksDir = getLocalBooksDirectory();
        dirs.add(localBooksDir);
//        if (!isExternalStorageReadable()) {
//            return new File[] {localBooksDir};
//        }
//        File[] externalStorageVolumes =
//                ContextCompat.getExternalFilesDirs(context, null);
//        if (externalStorageVolumes.length == 0) {
//            return new File[] {localBooksDir};
//        }
//        // Enhance: possibly we should include any "books" directories on any
//        // available external storage volumes.
//        File primaryExternalStorage = externalStorageVolumes[0];
//        File remoteBooksDir = new File(primaryExternalStorage, "books");
//        try {
//            // If possible create it. This is where we hope Bloom Desktop will be able to put books.
//            // Its existence signals BD to put USB transfers here.
//            remoteBooksDir.mkdirs();
//            if (remoteBooksDir.exists() && !remoteBooksDir.equals(localBooksDir))
//                return new File[] {localBooksDir, remoteBooksDir};
//        } catch (SecurityException e) {
//            // if we're not allowed to access it or create it, just ignore it.
//        }

        // There is one area of the SD card that this app is allowed to access
        // without asking the user for permission. In a release build it is the
        // directory which, when the card is mounted elsewhere, has the path
        // Android/data/org.sil.bloom.reader/files. If we find a books directory there,
        // we will use it, and not look for BloomExternal, which (if it exists)
        // is expected to be a duplicate.
        File privateSdCardBooksDir = privateSdCardBooksDir(context);
        if (privateSdCardBooksDir != null) {
            dirs.add(privateSdCardBooksDir);
        } else {
            // We'd like to try Android/data/org.sil.bloom.reader/files/Books, where privateSdCardBooksDir
            // would be if this were a release build. Android (at least 11+) won't let us, won't even
            // let the user give us permission, won't even let us know it exists. So the only other
            // thing that makes sense is to look for the BloomExternal directory. This was originally
            // designed for all BR channels to access after getting external storage permission, but
            // now also serves as a fall-back for non-release channels.
            File remoteStorageDir = IOUtilities.removablePublicStorageRoot(context);
            File remoteBooksDir = new File(remoteStorageDir, "BloomExternal");
            if (remoteBooksDir.exists() && !remoteBooksDir.equals(localBooksDir))
                dirs.add(remoteBooksDir);
        }

        return dirs.toArray(new File[dirs.size()]);
    }

    private boolean oldBloomDirectoryExistsButNoAccess(Context context) {
        File oldBloomDir = Environment.getExternalStoragePublicDirectory("Bloom");
        if (!oldBloomDir.exists()) return false;
        if (BaseActivity.haveLegacyStoragePermission(context)) return false; // we can access it with legacy permission.
        return !SAFUtilities.hasPermissionToBloomDirectory(context);
    }

    // Fill the collection with the books in these directories, plus any the user has selected
    // individually from BloomExternal if that is not one of the directories.
    private void loadFromDirectories(File[] booksDirs, Activity activity) {
        mShelfIds.clear();
        _booksAndShelves.clear();
        List<Uri> individualBooks = SAFUtilities.getBooksWithIndividualPermissions(activity);
        if (mInitializeTask != null) {
            int count = individualBooks.size() + (oldBloomDirectoryExistsButNoAccess(activity) ? 1 : 0);
            if (booksDirs != null && booksDirs.length > 0) {
                for (File booksDir : booksDirs) {
                    int newCount = 0;
                    try {
                        File[] files = IOUtilities.listFilesRecursively(booksDir,new FileFilter() {
                            public boolean accept(File file) {
                                String name = file.getName();
                                return  IOUtilities.isBloomPubFile(name) || name.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION);
                            }
                        });
                        newCount = files != null ? files.length : 0;
                    } catch (SecurityException e) {
                        // This is expected if we are on older Android and don't have external storage permission.
                    }
                    catch (NullPointerException e) {
                        // For some reason this is what actually happens in some cases (e.g., Nexus 5X API 28 emulator)
                        // when we don't have external storage permission
                    }
                    catch (Exception e) {
                        // And maybe other devices and OS versions will throw something else again?
                        e.printStackTrace();
                    }
                    if (newCount == 0) {
                        // Maybe it is a folder, typically ExternalFiles, that the user has given us
                        // permission to access using SAF? That doesn't allow us to listFiles, and
                        // unfortunately, instead of throwing or otherwise indicating that we don't
                        // have permission, listFiles just doesn't list any of them. So try it using
                        // SAF.
                        Uri uri = SAFUtilities.getUriForFolderWithPermission(activity, booksDir.getPath());
                        if (uri != null) {
                            newCount = SAFUtilities.countBooksIn(activity, uri);
                        } else {
                            newCount = 1; // We will make one placeholder for ExternalFiles
                            // (When it is clicked we ask for permission.)

                        }
                    }
                    count += newCount;
                }
            }
            mInitializeTask.setBookCount(count);
        }
        if (booksDirs != null && booksDirs.length > 0) {
            // Fix any duplicate .bloomd/.bloompub pairs in our main directory.
            for (File f:booksDirs[0].listFiles()) {
                fixBloomd(f.getAbsolutePath());
            }
            for (File booksDir : booksDirs)
                loadFromDirectory(booksDir, activity);
        }
        List<BookOrShelf> books = individualBooks.stream()
            .map(uri -> makeBookOrShelf(uri,null))
            .collect(Collectors.toList());
        if (oldBloomDirectoryExistsButNoAccess(activity)) {
            // Make a fake shelf for requesting access to it.
            String fakeShelfName = activity.getResources().getString(R.string.show_books_in_old_bloom_folder);
            BookOrShelf fakeShelf = new BookOrShelf(fakeShelfName + IOUtilities.BOOKSHELF_FILE_EXTENSION);
            fakeShelf.specialBehavior = "importOldBloomFolder";
            books.add(fakeShelf);
        }
        addBooks(books);
    }

    private void loadFromDirectory(File directory, Activity activity) {
        File[] files = IOUtilities.listFilesRecursively(directory, new FileFilter() {
            // Even when we DON'T HAVE PERMISSION to access files in the directory, we can see its
            // subdirectories!! But if we find no files, we're going to presume we have no permissions,
            // so we don't want to count directories here.
            @Override
            public boolean accept(File file) {
                return !file.isDirectory();
            }
        });
        ArrayList<BookOrShelf> books = new ArrayList<BookOrShelf>();
        if (files == null || files.length == 0 && !BaseActivity.haveLegacyStoragePermission(activity)) {
            // files may be null, or spuriously have length zero, if we don't have permission to access the folder,
            // or even if we DO have permission, but it's a folder we can only access through
            // SAF, like BloomExternal. So try again that way, if we get an empty list when we're
            // without legacy storage permission. Of course, if we do have that, the result should be correct.
            loadFromSAFDirectory(directory, activity);
            return;
        }
        for (int i = 0; i < files.length; i++) {
            final String name = files[i].getName();
            TextFileContent metaFile = new TextFileContent("meta.json");
            if (!IOUtilities.isBloomPubFile(name))
                continue; // not a book (nor a shelf)!
            final String path = files[i].getAbsolutePath();
            if (IOUtilities.isBloomPubFile(name) &&
                    !IOUtilities.isValidZipFile(new File(path), IOUtilities.CHECK_BLOOMPUB, metaFile)) {
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        String markedName = name + "-BAD";
                        Log.w("BloomCollection", "Renaming invalid book file " + path + " to " + markedName);
                        Context context = BloomReaderApplication.getBloomApplicationContext();
                        String message = context.getString(R.string.renaming_invalid_book, markedName);
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                    }
                });
                new File(path).renameTo(new File(path + "-BAD"));
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

    private void loadFromSAFDirectory(File directory, Activity activity) {
        // We didn't find anything in directory, but this might be because it's a directory we only
        // have permission to access through SAF.
        Uri uri = SAFUtilities.getUriForFolderWithPermission(activity, directory.getPath());
        final ArrayList<BookOrShelf> books = new ArrayList<BookOrShelf>();
        if (uri == null) {
            // This behavior is specific to the BloomExternal folder, therefore, not as generic as
            // "any folder we put in the list but can't get a uri for" above. So far, BloomExternal
            // is the only possible such folder. That could change. But probably we'd have permission
            // for any other folder, since we would have gotten it by asking the user.
            String fakeShelfName = activity.getResources().getString(R.string.show_books_on_sd_card);
            BookOrShelf fakeShelf = new BookOrShelf(fakeShelfName + IOUtilities.BOOKSHELF_FILE_EXTENSION);
            //fakeShelf.backgroundColor = "ffff00";
            fakeShelf.specialBehavior = "loadExternalFiles";
            books.add(fakeShelf);
            addBooks(books);
            return;
        }

        BookSearchListener listener = new BookSearchListener() {
            @Override
            public void onFoundBookOrShelf(File bloomPubFile, Uri bookOrShelfUri) {
                // in this context it isn't really new, but oh well..
                final String path = bookOrShelfUri.getPath();
                if (!IOUtilities.isBloomPubFile(path)
                        && !path.endsWith(IOUtilities.BOOKSHELF_FILE_EXTENSION))
                    return; // not a book (nor a shelf)!
                TextFileContent metaFile = new TextFileContent("meta.json");
                if (IOUtilities.isBloomPubFile(path) &&
                        !IOUtilities.isValidZipUri(bookOrShelfUri, IOUtilities.CHECK_BLOOMPUB, metaFile)) {
                    // Todo: can we find a way to hide the bad file??
//                    activity.runOnUiThread(new Runnable() {
//                        public void run() {
//                            String markedName = name + "-BAD";
//                            Log.w("BloomCollection", "Renaming invalid book file "+path+" to "+markedName);
//                            Context context = BloomReaderApplication.getBloomApplicationContext();
//                            String message = context.getString(R.string.renaming_invalid_book, markedName);
//                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
//                        }
//                    });
//                    new File(path).renameTo(new File(path+"-BAD"));
                    if (mInitializeTask != null) {
                        mInitializeTask.incrementBookProgress();
                    }
                    return;
                }
                books.add(makeBookOrShelf(bookOrShelfUri, metaFile));
                if (mInitializeTask != null) {
                    mInitializeTask.incrementBookProgress();
                }
            }

            @Override
            public void onFoundBundle(Uri bundleUri) {

            }

            @Override
            public void onSearchComplete() {

            }
        };
        SAFUtilities.searchDirectoryForBooks(activity, uri, listener);
        addBooks(books);
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
        if (book.pathOrUri != null) {
            File file = new File(book.pathOrUri);
            if (file.exists()) {
                file.delete();
            }
        }
        _booksAndShelves.remove(book);
        mFilteredBooksAndShelves.remove(book);
    }

    // is this coming from somewhere other than where we store books?
    // then move or copy it in.
    // Returns the path to the book (or toString of URI if not copied to private storage),
    // and a boolean indicating whether it was added.
    public Pair<String, Boolean> ensureBookOrShelfIsInCollection(Context context, Uri bookOrShelfUri) {
        if (bookOrShelfUri == null || bookOrShelfUri.getPath() == null)
            return null; // Play console proves this is possible somehow

        // Possible for this to happen if we load a .bloompub/.bloomd directly without loading the whole app first (BL-8218)
        if (mLocalBooksDirectory == null)
            mLocalBooksDirectory = getLocalBooksDirectory();

        // We need the extra slash so that e.g. books in "Bloom - Copy" are not treated as already in Bloom.
        if (bookOrShelfUri.getPath().contains(mLocalBooksDirectory.getAbsolutePath() + "/"))
            return new Pair<>(bookOrShelfUri.getPath(), false);

        // If the book is in BloomExternal, we will neither copy nor move it, just read it directly
        // from there. Calling code will already have made sure we persist permission to use the
        // book so it will get added each time we start up.
        if (SAFUtilities.isUriInBloomExternal(context, bookOrShelfUri)) {
            String bookPath = bookOrShelfUri.toString();
            BookOrShelf existingBook = getBookOrShelfByPath(bookPath);
            if (existingBook != null) {
                return new Pair<>(bookPath, false);
            }
            addBookOrShelf(bookPath, null);
            return new Pair<>(bookPath, true);
        }


        String filename = IOUtilities.getFileNameFromUri(context, bookOrShelfUri);
        String destination = mLocalBooksDirectory.getAbsolutePath() + File.separator + filename;
        destination = IOUtilities.ensureFileNameHasNoEncodedExtension(destination);
        File destFile = new File(destination);
        if (destFile.exists() && destFile.lastModified() < IOUtilities.lastModified(context, bookOrShelfUri)) {
            return new Pair<>(destination, false);
        }
        Log.d("BloomReader", "Copying book into Bloom directory");
        boolean copied = IOUtilities.copyBookOrShelfFile(context, bookOrShelfUri, destination);
        if (copied){
			destination = fixBloomd(destination);
            destination = FixDuplicate(destination);
            // it's probably not in our list that we display yet, so make an entry there
            addBookOrShelfIfNeeded(destination);
            return new Pair<>(destination, true);
        } else{
            return null;
        }
    }

    // If we can determine that newBloomFile is the same book as one that we have already,
    // replace the existing one with the new one, and return its path.
    // The particular kind of duplicates we're looking for are those browsers generate
    // when the user downloads a new version of a book. So if the newBloomFile looks like
    // X (n) and we already have a book X, we'll check to see if they are the same.
    // Ideally, we might check all books, but I think in general that will be too slow.
    String FixDuplicate(String newBloomFile){
        final File bloomFile = new File(newBloomFile);
        File parent = bloomFile.getParentFile();
        String name = bloomFile.getName();
        int index = name.lastIndexOf("(");

        if (index < 0)
            return newBloomFile; // Can't be this sort of duplicate
        String similarBookName = name.substring(0, index);
        // Trim white space from the end...but not at the beginning, in the unlikely event of any being there.
        int lastSpace = similarBookName.lastIndexOf(" ");
        if (lastSpace >= 0)
            similarBookName = similarBookName.substring(0,lastSpace);

        // This is what we'd expect the original to be called if previously downloaded.
        // (We don't think we need to handle .bloomd files here. Both the incoming path and
        // the ones aleady in the Bloom directory should already be .bloompub.)
        String possibleMatch = parent.getPath() + File.separator + similarBookName + ".bloompub";
        final File similarBookFile = new File(possibleMatch);
        if (!similarBookFile.exists())
            return newBloomFile; // we don't have a book at the expected location. Maybe the book name really has parens! Or deleted previously.

        String oldId = getBookId(similarBookFile);
        String newId = getBookId(bloomFile);
        if (!oldId.equals(newId) || oldId == null)
            return newBloomFile; // can't confirm they are the same book, keep both

        // They are the same! Fix things.
        similarBookFile.delete();
        bloomFile.renameTo(similarBookFile);
        return possibleMatch;
// Some of this research might be useful if we decide on a more complex approach to
// finding possible matches.
//        final String searchFor = similarBookName; // must be final to use in filter
//        FilenameFilter filter = new FilenameFilter() {
//
//            public boolean accept(File f, String name)
//            {
//                return name.startsWith(searchFor);
//            }
//        };
//        File[] matches = parent.listFiles(filter);
    }

    String getBookId(File bloomFile) {
        try {
            byte[] metaBytes = IOUtilities.ExtractZipEntry(bloomFile, "meta.json");
            String json = new String(metaBytes, "UTF-8");
            JSONObject data = new JSONObject(json);
            return data.getString("bookInstanceId");
        } catch (Exception e) {
            return null;
        }
    }

    // If the path passed ends in the obsolete .bloomd, rename it to .bloompub.
    // If that results in a conflict, delete the older file and keep the newer one with the
    // correct name.
    // Return the (possibly corrected) name.
    public static String fixBloomd(String currentPath) {
        if (!currentPath.endsWith(".bloomd")) return currentPath;
        File currentFile = new File(currentPath);
        String newPath = currentPath.substring(0, currentPath.length() - "bloomd".length()) + "bloompub";
        if (!currentFile.exists()) return newPath; // may have already been fixed in a previous call
        File newFile = new File(newPath);
        if (newFile.exists()) {
            if (newFile.lastModified() > currentFile.lastModified()) {
                // we'll keep the existing 'new' file
                currentFile.delete();
                return newPath;
            }
            newFile.delete();
        }
        currentFile.renameTo(newFile);
        return newPath;
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
    // Extracts the meta.json entry from the bloompub/bloomd file, extracts the tags from that,
    // finds any that start with "bookshelf:", and sets the balance of the tag as one of the
    // book's shelves.  The meta.json data may or may not have already been extracted.
    public static void setShelvesAndTitleOfBook(BookOrShelf bookOrShelf, TextFileContent metaFile) {
        String json;
        try {
            if (bookOrShelf.isShelf()) {
                json = bookOrShelf.uri == null
                        ? IOUtilities.FileToString(new File(bookOrShelf.pathOrUri))
                        : IOUtilities.UriToString(BloomReaderApplication.getBloomApplicationContext(), bookOrShelf.uri);
            }
            else {
                if (metaFile != null && metaFile.Content != null && !metaFile.Content.isEmpty()) {
                    json = metaFile.Content;
                } else {
                    byte[] jsonBytes = bookOrShelf.uri == null
                            ? IOUtilities.ExtractZipEntry(new File(bookOrShelf.pathOrUri), "meta.json")
                            : IOUtilities.ExtractZipEntry(BloomReaderApplication.getBloomApplicationContext(), bookOrShelf.uri, "meta.json");
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
            // a bloompub/bloomd does not contain valid meta.json from which we can extract tags is that
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
                if (thumb.lastModified() < book.lastModified()) {
                    thumb.delete();
                    return new BloomFileReader(context, book).getThumbnail(thumbsDirectory);
                }
                return Uri.fromFile(thumb);
            }

            File noThumb = new File(thumbsDirectory.getPath() + File.separator + NO_THUMBS_DIR + File.separator + book.name);
            if (noThumb.exists()){
                if (noThumb.lastModified() < book.lastModified()) {
                    noThumb.delete();
                    return new BloomFileReader(context, book).getThumbnail(thumbsDirectory);
                }
                return null;
            }

            return new BloomFileReader(context, book).getThumbnail(thumbsDirectory);
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
