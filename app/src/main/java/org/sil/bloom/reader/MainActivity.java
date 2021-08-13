package org.sil.bloom.reader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;

import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.BookOrShelf;
import org.sil.bloom.reader.wifi.GetFromWiFiActivity;

import java.io.File;
import java.util.Date;
import java.util.List;

import static org.sil.bloom.reader.IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOK_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.ENCODED_FILE_EXTENSION;


public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener, BookListAdapter.BookClickListener{

    public static final String NEW_BOOKS = "newBooks";
    protected BookCollection _bookCollection;
    public android.view.ActionMode contextualActionBarMode;
    private static boolean sSkipNextNewFileSound;

    // OnCreate may run a second time if the activity gets recycled and the user navigates back to it
    // but we don't want to reopen the book if we already did
    private boolean alreadyOpenedFileFromIntent = false;
    private static final String ALREADY_OPENED_FILE_FROM_INTENT_KEY = "alreadyOpenedFileFromIntent";

    private boolean showMessageOnLocationPermissionGranted = false;
    private boolean hasPreviouslyResumed = false;

    private RecyclerView mBookRecyclerView;
    BookListAdapter mBookListAdapter;       // accessed by InitializeLibraryTask
    // Keeps track of the state of an ongoing File Search
    private FileSearchState mFileSearchState;

    // Dynamically created/destroyed progress bar and text view used during initial loading.
    ProgressBar mLoadingProgressBar;       // accessed by InitializeLibraryTask
    TextView mLoadingTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Before we create the main activity, so it will see any migrated books.
        copyFromBooksDirectory();
        createMainActivity(savedInstanceState);
        requestLocationAccess();
        requestLocationUpdates();
    }

    // If we haven't already requested the user to do the necessary stuff so we can send
    // location analytics, do so now. But only once for each request.
    private void requestLocationAccess() {
        Settings settings = Settings.load(this);
        if (!settings.haveRequestedLocation()) {
            if (!haveLocationPermission(this)) {
                // Just once, we will ask for this permission. First explain why, since the
                // system dialog can't be customized and doesn't give any reason.
                settings.setHaveRequestedLocation(true);
                settings.save(this);
                AlertDialog alertDialog = new AlertDialog.Builder(this, R.style.AlertDialogTheme).create();
                alertDialog.setTitle(getString(R.string.share_location));
                alertDialog.setMessage(getString(R.string.request_location));
                final boolean[] wasDontAllowPressed = {false}; // we just want a boolean, but to use in callbacks has to be final.
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.allow),
                        (dialog, which) -> dialog.dismiss());
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dont_allow),
                        (dialog, which) -> {
                            wasDontAllowPressed[0] = true;
                            dialog.dismiss();
                        });
                alertDialog.setOnDismissListener(dialog -> {
                    // We want this to happen even if the user dismisses the dialog
                    // by tapping outside it rather than by tapping OK.
                    // But not if they actually tapped "don't allow"
                    if (!wasDontAllowPressed[0]) {
                        requestLocationPermission();
                    }
                });
                alertDialog.show();
            }
        }
        if (!settings.haveRequestedTurnOnGps()) {
            // We may want to request turning on the GPS. Often this won't happen, because
            // on the very first run, the permission will be off, so the first condition
            // here will fail. Note, however, that if the user grants permission but location
            // is disabled, the handler for receiving notification of the permission grant
            // will requestTurnOnGps(). The code here is useful if the user later turns the
            // location service off, to let him know (just once! we won't nag) that this is
            // a problem for BR.
            // Of course we only need do it even once if location is in fact turned off.
            if (haveLocationPermission(this) && !isLocationEnabled(this)) {
                // Just once, we will ask them to do this. First explain why.
                settings.setHaveRequestedTurnOnGps(true);
                settings.save(this);
                // We want just a boolean. But only 'final' objects can be accessed, as this one
                // is, in event handlers. So we make a 'final' array of one boolean, which we
                // can change in the OK event handler. This flag lets the dismiss dialog event
                // handler know that the dialog was dismissed by the OK button.
                final boolean[] doTurnOn = new boolean[1];
                doTurnOn[0] = false;
                AlertDialog alertDialog = new AlertDialog.Builder(this, R.style.AlertDialogTheme).create();
                alertDialog.setTitle(getString(R.string.turn_on_location));
                alertDialog.setMessage(getString(R.string.request_gps));
                alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok),
                        (dialog, which) -> {
                            doTurnOn[0] = true;
                            dialog.dismiss();
                            // It doesn't seem to work to requestTurnOnGps() here.
                            // I think the problem is that the switch from the dialog
                            // activity back to this caused by dismiss() beats the switch
                            // to the system settings dialog.
                        });
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                        (dialog, which) -> dialog.dismiss());
                alertDialog.setOnDismissListener(dialog -> {
                    // This gets set true if it was the OK button that dismissed the dialog.
                    // There must be a better way to know that, but I'm sick of looking.
                    if (doTurnOn[0]) {
                        requestTurnOnGps();
                    }
                    Properties p = new Properties();
                    p.putValue("granted", doTurnOn[0]);

                    Context context = BloomReaderApplication.getBloomApplicationContext();
                    if (context != null)
                        Analytics.with(context).track("requestGps", p);
                });
                alertDialog.show();
            }
        }
    }

    // Ask the system to send us location updates hourly. We don't actually want them, but
    // we do want to ensure that getLastKnownLocation() provides reasonably current information.
    private void requestLocationUpdates() {
        if (!haveLocationPermission(this)) {
            return;
        }
        LocationManager lm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            // We saw this happen in the Play console
            Log.e("locationError", "unexpectedly unable to get a location manager");
            return;
        }
        long minTimeMs = 60*60*1000; // hourly
        // an hourly check is not expensive, and we want the age to come out under an hour if we're
        // getting them, so don't bother with a minimum distance.
        long minDistanceM = 0;
        try {
            List<String> allProviders = lm.getAllProviders();
            String provider = null;
            if (allProviders.contains(LocationManager.GPS_PROVIDER))
                provider = LocationManager.GPS_PROVIDER;
            else if (allProviders.contains(LocationManager.NETWORK_PROVIDER))
                provider = LocationManager.NETWORK_PROVIDER;
            else if (allProviders.contains(LocationManager.PASSIVE_PROVIDER))
                provider = LocationManager.PASSIVE_PROVIDER;
            if (provider == null) {
                Log.e("locationError", "no location provider available");
                return;
            }
            lm.requestLocationUpdates(provider, minTimeMs, minDistanceM, new LocationListener() {
                // We don't actually want the information, we just want lastKnownLocation to be
                // reasonably current. But the API requires these to be implemented.
                @Override
                public void onLocationChanged(@NonNull Location location) {
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(@NonNull String provider) {
                    // Enhance: seems to get called when the user turns it on, even in the
                    // background. Could possibly be used to show the dialog, if the user
                    // was put into the settings screen as a result of asking to check
                    // analytics location data. But things are complicated enough already!
                }

                @Override
                public void onProviderDisabled(@NonNull String provider) {
                }
            });
        } catch (SecurityException se) {
            Log.e("locationError", "unexpectedly forbidden to request location");
        } catch (Exception e) {
            // Don't crash the program just because we can't update the location.
            // We were getting IllegalArgumentExceptions at one point.
            Log.e("locationError", "unexpectedly unable to request location");
        }
    }

    public static boolean haveLegacyStoragePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestLegacyStoragePermission(int requestCode) {
        String[] permissionsNeeded = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        ActivityCompat.requestPermissions(this, permissionsNeeded, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // This override is the way we get results of ActivityCompat.requestPermissions().
        if (permissions.length <= 0)
            return;
        switch(requestCode) {
            case LOCATION_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (showMessageOnLocationPermissionGranted) {
                        showMessageOnLocationPermissionGranted = false;
                        showLocationMessage();
                    }
                    if (!isLocationEnabled(this)) {
                        // They just gave us permission, so hopefully this isn't unexpected.
                        // The pre-permission dialog does also indicate that this might be needed.
                        requestTurnOnGps();
                    }
                    // Usually done when the activity is created, but if we didn't have permission
                    // to do it then, we should now.
                    requestLocationUpdates();
                }
                break;
            case STORAGE_PERMISSION_USB:
                // This is permission to read/write to the `InternalStorage/Bloom` directory
                // where the transfer via USB puts books.
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    getFromUsbPreAndroid11();
                } else {
                    getFromUsbWithSaf();
                }
                break;
            case STORAGE_PERMISSION_SEARCH:
                // The two requested are write and read
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    searchForBloomBooks_preAndroid11();
                } else {
                    searchForBloomBooksUsingSaf();
                }
                break;
        }
    }

    // Copy books from the "Books" directory to our own if they are new.
    // This is done when Bloom starts up and has two purposes.
    // First, if this is the first time we've run the version of Bloom that uses
    // a private directory instead of "Books", we need to migrate the books.
    // This should only be the case if the user has upgraded, so we should have legacy access
    // to the directory.
    // Second, if a book has been sent to the Books directory over USB (or otherwise) while we
    // were paused, we need to get it.
    // Returns the path to the most recently updated book, or null if none was updated.
    private String copyFromBooksDirectory() {
        File oldBloomDir = Environment.getExternalStoragePublicDirectory("Bloom");
        File newBloomDir = BookCollection.getLocalBooksDirectory();
        boolean failure = false;
        String[] mostRecentModifiedBook = {null};
        long mostRecentCopiedBookModified = 0;
        try {
            if (!oldBloomDir.exists()) {
                return null; // nothing to copy
            }
            // It's slightly wasteful to do this if we already have. However, in the release build,
            // we delete the directory after copying it, so it will only happen once. Someone who
            // is messing with alpha or beta might like to see any new books they fetch with the
            // old release build.
            boolean preserveOldDirectory = BuildConfig.DEBUG || BuildConfig.FLAVOR.equals("alpha") || BuildConfig.FLAVOR.equals("beta");
            if (canUseGeneralStorageAccess()) {
                File[] filesInOldBloomDir = oldBloomDir.listFiles();
                if (filesInOldBloomDir == null)
                    return null;
                for (File f : filesInOldBloomDir) {
                    String fileName = f.getName();
                    if (fileName.equals(".thumbs")) {
                        continue; // this is a directory, and the data can be rebuilt, so save time by not copying
                    }
                    File dest = new File(newBloomDir, fileName);
                    if (dest.exists()) {
                        continue; // Don't re-copy, and especially don't overwrite a possibly newer version.
                    }
                    long modifyTime = f.lastModified();
                    if (modifyTime > mostRecentlyModifiedBloomFileTime) {
                        mostRecentlyModifiedBloomFileTime = modifyTime;
                        mostRecentModifiedBook[0] = f.getAbsolutePath();
                    }
                    if (preserveOldDirectory) {
                        IOUtilities.copyFile(f.getPath(), dest.getPath());
                    } else {
                        failure |= !f.renameTo(dest);
                    }
                }
            } else if (SAFUtilities.hasPermissionToBloomDirectory(this)) {
                // try using SAF
                final Context context = this; // inside the listener, 'this' is the listener
                SAFUtilities.searchDirectoryForBooks(this, SAFUtilities.BloomDirectoryTreeUri, new BookSearchListener() {
                    @Override
                    public void onNewBookOrShelf(File bloomdFile, Uri bookOrShelfUri) {
                        String fileName = IOUtilities.getFileNameFromUri(context, bookOrShelfUri);
                        File privateStorageFile = new File(newBloomDir + "/" + fileName);
                        final long bloomDirectoryModifiedTime = IOUtilities.lastModified(context, bookOrShelfUri);
                        if (privateStorageFile.exists() && privateStorageFile.lastModified() >= bloomDirectoryModifiedTime)
                            return; // already have this version of book, or an even newer one
                        if (bloomDirectoryModifiedTime>mostRecentlyModifiedBloomFileTime) {
                            mostRecentlyModifiedBloomFileTime = bloomDirectoryModifiedTime;
                            mostRecentModifiedBook[0] = privateStorageFile.getAbsolutePath();
                        }
                        SAFUtilities.copyUriToFile(context, bookOrShelfUri, privateStorageFile);
                        //if (!preserveOldDirectory) {
                            SAFUtilities.deleteUri(context, bookOrShelfUri);
                        //}
                    }

                    @Override
                    public void onNewBloomBundle(Uri bundleUri) {

                    }

                    @Override
                    public void onSearchComplete() {

                    }
                });

            }
        }
        catch (SecurityException e) {
            Log.e("migrateLegacyData", e.getMessage());
        }
        return mostRecentModifiedBook[0];
    }

    private void createMainActivity(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);
        BloomReaderApplication.setupVersionUpdateInfo(this); // may use analytics, so must run after it is set up.

        _bookCollection = setupBookCollection();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayShowTitleEnabled(false);


        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        configureActionBar(toggle);

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        // This menu option should not be shown in production.
        if (!BuildConfig.DEBUG && !BuildConfig.FLAVOR.equals("alpha")) {
            navigationView.getMenu().removeItem(R.id.nav_test_location_analytics);
        }

        mBookRecyclerView = findViewById(R.id.book_list2);
        SetupCollectionListView(mBookRecyclerView);

        // If we were started by some external process, we need to process any file
        // we were given (a book or bundle)
        if (savedInstanceState != null)
            alreadyOpenedFileFromIntent = savedInstanceState.getBoolean(ALREADY_OPENED_FILE_FROM_INTENT_KEY, false);
        processIntentData();

        // Insert the build version and date into the appropriate control.
        // We have to find it indirectly through the navView's header or it won't be found
        // this early in the view construction.
        TextView versionDate = navigationView.getHeaderView(0).findViewById(R.id.versionDate);
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            Date buildDate = new Date(BuildConfig.TIMESTAMP);
            String date = DateFormat.format("dd MMM yyyy", buildDate).toString();
            versionDate.setText(getVersionAndDateText(versionName, date));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        // Cleans up old-style thumbnails - could be removed someday after it's run on most devices with old-style thumbnails
        BookCollection.cleanUpOldThumbs(this);

        resumeMainActivity();
    }

    private String getVersionAndDateText(String versionName, String date) {
        // Not bothering trying to internationalize this for now...
        return versionName + ", " + date;
    }

    // This is a hook to allow ShelfActivity to disable the navigation drawer and replace it
    // with a back button.
    protected void configureActionBar(ActionBarDrawerToggle toggle) {
    }

    // ShelfActivity does this differently.
    protected BookCollection setupBookCollection() {
        BloomReaderApplication.theOneBookCollection = new BookCollection();
        new InitializeLibraryTask(this).execute();
        return BloomReaderApplication.theOneBookCollection;
    }

    private void processIntentData() {
        // It's not necessary to split these two lines apart, but it sure makes debugging intents easier!
        Intent intent = getIntent();
        Uri uri = intent.getData();
        if (uri == null || alreadyOpenedFileFromIntent)
            return;
        Log.i("Intents", "processing "+intent.toString());
        String nameOrPath = IOUtilities.getFileNameOrPathFromUri(this, uri);
        if (nameOrPath == null) // reported as crash on Play console
            return;
        if (nameOrPath.endsWith(BOOK_FILE_EXTENSION) ||
                nameOrPath.endsWith(BOOK_FILE_EXTENSION + ENCODED_FILE_EXTENSION)) {
            importBook(uri, true);
        } else if (nameOrPath.endsWith(BLOOM_BUNDLE_FILE_EXTENSION)) {
            importBloomBundle(uri);
        } else {
            Log.e("Intents", "Couldn't figure out how to open URI: " + uri.toString());
            return; // keep BR from saying we opened it, when we didn't!
        }
        alreadyOpenedFileFromIntent = true;
    }

    // Copy a book to our books folder and add it to the library.
    // If we're importingOneFile (ie not doing a FileSearch of the device),
    // we'll go ahead and open the book and do file cleanup.
    // Return value indicates success.
    private boolean importBook(Uri bookUri, boolean importingOneFile) {
        String newPath = _bookCollection.ensureBookIsInCollection(this, bookUri);
        if (newPath != null) {
            if (importingOneFile) {
                openBook(this, newPath);
                new FileCleanupTask(this).execute(bookUri);
            }
            else
                updateForNewBook(newPath);
            return true;
        } else {
            Log.e("BookSearchFailedImport", bookUri.getPath());
            String filename = IOUtilities.getFileNameFromUri(this, bookUri);
            final AlertDialog d = new AlertDialog.Builder(this, R.style.SimpleDialogTheme)
                    .setPositiveButton(android.R.string.ok, null)
                    .setTitle(R.string.failed_book_import)
                    .setMessage(String.format(getString(R.string.failed_book_import2), filename))
                    .create();
            d.show();
            return false;
        }
    }

    private void importBloomBundle(Uri bloomBundleUri) {
        new ImportBundleTask(this).execute(bloomBundleUri);
    }

    // Called by ImportBundleTask and when we get permission to BloomExternal
    public void reloadBookList() {
        // Reinitialize completely to get the new state of things.
        _bookCollection.init(this, null);
        // Don't highlight the set of new books, just update the list displayed. (BL-8808)
        mBookListAdapter.notifyDataSetChanged();
        resetFileObserver(); // Prevent duplicate notifications
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeMainActivity();
    }

    private void resumeMainActivity() {
        updateFilter();
        if (!hasPreviouslyResumed) {
            // If this resume immediately follows create, we don't need to do this again.
            // Otherwise, look for new books since pause.
            hasPreviouslyResumed = true;
            String oneNewFile = copyFromBooksDirectory();
            if (oneNewFile != null) {
                updateForNewBook(oneNewFile);
            }
        }
        // we will get notification through onNewOrUpdatedBook if Bloom pushes a new or updated
        // book to our directory using MTP. Under Android 11 or later, we will only get them
        // if the user has at some point selected "Receive books via USB" and granted permission.
        startObserving();
        // And right now we will trigger the notification if anyone or anything has changed a
        // book in our folder while we were paused.
        //notifyIfNewFileChanges();
        // import any new books that showed up in the USB transfer directory while we were paused
        String bookToHighlight = ((BloomReaderApplication) this.getApplication()).getBookToHighlight();
        if (bookToHighlight != null) {
            updateForNewBook(bookToHighlight);
            ((BloomReaderApplication) this.getApplication()).setBookToHighlight(null);
        }

        //Periodic cleanup
        SharingManager.fileCleanup(this);
    }

    // a hook to allow ShelfActivity to set a real filter.
    // We need to set it to nothing here for when we return to the main activity from a shelf.
    protected void updateFilter() {
        _bookCollection.setFilter("");
    }

    @Override
    protected void onPause() {
        stopObserving();

        super.onPause();
    }

    @Override
    protected void onNewOrUpdatedBook(String filePathOrUri) {
        final String filePathOrUriLocal = filePathOrUri;
        runOnUiThread(() -> updateForNewBook(filePathOrUriLocal));
    }

    public static void skipNextNewFileSound() {
        sSkipNextNewFileSound = true;
    }

    private void updateForNewBook(String filePathOrUri) {
        BookOrShelf book = _bookCollection.addBookIfNeeded(filePathOrUri);
        refreshList(book);
        if (sSkipNextNewFileSound) {
            sSkipNextNewFileSound = false;
        }
        else {
            playSoundFile(R.raw.bookarrival);
        }
        Toast.makeText(MainActivity.this, book.name + " added or updated", Toast.LENGTH_SHORT).show();
    }

    private void refreshList(BookOrShelf book) {
        if (book == null || mBookListAdapter == null)
            return;

        int bookPosition = mBookListAdapter.highlightItem(book);

        if (bookPosition > -1)
            smoothScrollToPosition(bookPosition);
    }

    private void highlightItems(List<String> paths) {
        if (paths == null)
            return;
        int firstHighlightedIndex = mBookListAdapter.highlightItems(paths);
        if (firstHighlightedIndex > -1)
            smoothScrollToPosition(firstHighlightedIndex);
    }

    private void smoothScrollToPosition(int position){
        final LinearLayoutManager layoutManager = (LinearLayoutManager) mBookRecyclerView.getLayoutManager();
        RecyclerView.SmoothScroller scroller = new LinearSmoothScroller(this) {
            @Override
            public PointF computeScrollVectorForPosition(int targetPosition) {
                return layoutManager.computeScrollVectorForPosition(targetPosition);
            }

            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }
        };
        scroller.setTargetPosition(position);
        layoutManager.startSmoothScroll(scroller);
    }

    private void closeContextualActionBar() {
        if (contextualActionBarMode != null)
            contextualActionBarMode.finish();
    }

    private void shareBookOrShelf(){
        BookOrShelf bookOrShelf = mBookListAdapter.getSelectedItem();
        if (bookOrShelf == null) {
            // Not sure how this can happen, but it did
            return;
        }
        if (bookOrShelf.isShelf())
            shareShelf(bookOrShelf);
        else
            shareBook(bookOrShelf);
    }

    private void shareBook(BookOrShelf book){
        new SharingManager(this).shareBook(this, book);
    }

    private void shareShelf(BookOrShelf shelf){
        List<BookOrShelf> booksAndShelves = _bookCollection.getAllBooksWithinShelf(shelf);
        ShareShelfDialogFragment dialogFragment = new ShareShelfDialogFragment();
        dialogFragment.setBooksAndShelves(booksAndShelves);
        dialogFragment.show(getSupportFragmentManager(), ShareShelfDialogFragment.FRAGMENT_TAG);
    }

    private void deleteBookOrShelf(){
        BookOrShelf bookOrShelf = mBookListAdapter.getSelectedItem();

        // Somehow, the pre-launch tests on the Play console were able to get this to be null
        if (bookOrShelf == null)
            return;

        if (bookOrShelf.isShelf())
            deleteShelf(bookOrShelf);
        else
            deleteBook(bookOrShelf);
    }

    private void deleteShelf(final BookOrShelf shelf){
        final List<BookOrShelf> booksAndShelves = _bookCollection.getAllBooksWithinShelf(shelf);
        String message;
        if (booksAndShelves.size() == 1)
            message = getString(R.string.deleteExplanationEmptyShelf, shelf.name);
        else
            message = getString(R.string.deleteExplanationShelf, booksAndShelves.size(), shelf.name);
        new AlertDialog.Builder(this, R.style.SimpleDialogTheme).setMessage(message)
                .setTitle(R.string.deleteConfirmation)
                .setPositiveButton(R.string.deleteConfirmButton, (dialog, i) -> {
                    Log.i("BloomReader", "DeleteShelf " + shelf.toString());
                    for(BookOrShelf b : booksAndShelves)
                        _bookCollection.deleteFromDevice(b);
                    mBookListAdapter.notifyDataSetChanged();
                    closeContextualActionBar();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void deleteBook(final BookOrShelf book) {
        new AlertDialog.Builder(this, R.style.SimpleDialogTheme).setMessage(getString(R.string.deleteExplanationBook, book.name))
                .setTitle(getString(R.string.deleteConfirmation))
                .setPositiveButton(getString(R.string.deleteConfirmButton), (dialog, which) -> {
                    Log.i("BloomReader", "DeleteBook "+ book.toString());
                    _bookCollection.deleteFromDevice(book);
                    mBookListAdapter.notifyDataSetChanged();
                    closeContextualActionBar();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void SetupCollectionListView(final RecyclerView listView) {
        listView.setLayoutManager(new LinearLayoutManager(this));
        mBookListAdapter = new BookListAdapter(_bookCollection, this);
        listView.setAdapter(mBookListAdapter);
    }

    @Override
    public void onBookClick(BookOrShelf bookOrShelf){
        openBook(this, bookOrShelf.pathOrUri);
    }

    @Override
    public boolean onBookLongClick(final BookOrShelf selectedBookOrShelf){
        contextualActionBarMode = startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                mode.getMenuInflater().inflate(R.menu.book_item_menu, menu);
                menu.findItem(R.id.delete).setVisible(selectedBookOrShelf.isDeleteable());
                return true;
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(android.view.ActionMode mode, MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.share) {
                    shareBookOrShelf();
                    mode.finish();
                    return true;
                } else if (itemId == R.id.delete) {
                    deleteBookOrShelf();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode mode) {
                mBookListAdapter.unselect(selectedBookOrShelf);
                contextualActionBarMode = null;
            }
        });

        return true;
    }

    @Override
    public void onClearBookSelection(){
        closeContextualActionBar();
    }

    private void openBook(Context context, String path) {
        BookOrShelf bookOrShelf = _bookCollection.getBookOrShelfByPath(path);
        if (bookOrShelf == null) {
            // Play console shows this can happen somehow.
            // Maybe we when fix the concurrency issues, this goes away, too.
            return;
        }
        if (bookOrShelf.specialBehavior == "loadExternalFiles") {
            AskUserForPermissionToReadBloomExternal();
            return;
        }
        if (bookOrShelf.uri == null && !new File(path).exists()) {
            // Possibly deleted - possibly on an sd card that got removed
            Toast.makeText(this, getString(R.string.missing_book, BookOrShelf.getNameFromPath(path)), Toast.LENGTH_LONG).show();
            // Remove the book from the collection
            _bookCollection.deleteFromDevice(_bookCollection.getBookOrShelfByPath(path));
            mBookListAdapter.notifyDataSetChanged();
            return;
        };
        // Enhance: is there a way to usefully check for  uris to things that don't exist?
        if (bookOrShelf.isShelf()) {
            Intent intent = new Intent(context, ShelfActivity.class);
            intent.putExtra("filter", bookOrShelf.shelfId);
            intent.putExtra("label", bookOrShelf.name); // Or get it from the appropriate ws of label
            intent.putExtra("background", bookOrShelf.backgroundColor);
            context.startActivity(intent);
        } else {
            Intent intent = new Intent(context, ReaderActivity.class);
            intent.putExtra("bookPath", path);
            if (bookOrShelf.uri != null) {
                intent.putExtra("bookUri", bookOrShelf.uri.toString());
            }
            intent.putExtra("brandingProjectName", bookOrShelf.brandingProjectName);
            context.startActivity(intent);
        }
    }

    private void AskUserForPermissionToReadBloomExternal() {
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.ic_use_this_folder);
        AlertDialog.Builder builder =
                new AlertDialog.Builder(this, R.style.AlertDialogTheme).
                        setTitle(R.string.show_books_on_sd_card).
                        setMessage(getString(R.string.please_click_use_this_folder)).
                        setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).
                        setOnDismissListener((new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                LaunchActivityForExternalFilesPermission();
                            }
                        })).
                        setView(image);
        final AlertDialog dlg = builder.create();
        // In case the user doesn't understand and clicks the button in the image, go ahead and
        // show the real one.
        image.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                dlg.dismiss();
                return false;
            }
        });
        dlg.show();
    }

    private void LaunchActivityForExternalFilesPermission() {
        Intent permissionIntent = SAFUtilities.getDirectoryPermissionIntent(SAFUtilities.getExternalFilesDirUri(this));
        // This apparently has no effect. If it ever does, we should plan to allow it to be localized.
        permissionIntent.putExtra(DocumentsContract.EXTRA_PROMPT, "Allow Bloom to access books on SD card");
        mGetExternalFilesDirectoryUri.launch(permissionIntent);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

 //maybe someday. This is the 3-vertical dot menu
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        //getMenuInflater().inflate(R.menu.main, menu);
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
//    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.delete) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    static final int LOCATION_PERMISSION = 1;
    static final int STORAGE_PERMISSION_USB = 2;
    static final int STORAGE_PERMISSION_SEARCH = 3;

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        if (id == R.id.nav_get_wifi) {
            Intent intent = new Intent(this, GetFromWiFiActivity.class);
            mGetBooksFromWiFi.launch(intent);
        } else if (id == R.id.nav_get_usb) {
            GetFromUsb();
        } else if (id == R.id.nav_share_app) {
            ShareDialogFragment shareDialogFragment = new ShareDialogFragment();
            shareDialogFragment.show(getFragmentManager(), ShareDialogFragment.SHARE_DIALOG_FRAGMENT_TAG);
        } else if (id == R.id.nav_share_books) {
            ShareAllBooksDialogFragment shareBooksDialogFragment = new ShareAllBooksDialogFragment();
            shareBooksDialogFragment.show(getSupportFragmentManager(), ShareAllBooksDialogFragment.SHARE_BOOKS_DIALOG_FRAGMENT_TAG);
        } else if (id == R.id.nav_release_notes) {
            DisplaySimpleResource(getString(R.string.release_notes), R.raw.release_notes);
        } else if (id == R.id.nav_search_for_bundles) {
            searchForBloomBooks();
        } else if (id == R.id.nav_test_location_analytics) {
            showLocationAnalyticsData();
        } else if (id == R.id.about_reader) {
            DisplaySimpleResource(getString(R.string.about_bloom_reader), R.raw.about_reader);
        } else if (id == R.id.about_bloom) {
            DisplaySimpleResource(getString(R.string.about_bloom), R.raw.about_bloom);
        } else if (id == R.id.about_sil) {
            DisplaySimpleResource(getString(R.string.about_sil), R.raw.about_sil);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void GetFromUsb() {
        if (osAllowsGeneralStorageAccess()) {
            if (haveLegacyStoragePermission(this)) {
                getFromUsbPreAndroid11();
            } else {
                requestLegacyStoragePermission(STORAGE_PERMISSION_USB);
            }
            return;
        }

        getFromUsbWithSaf();
    }

    private void getFromUsbPreAndroid11() {
        final AlertDialog d = new AlertDialog.Builder(this, R.style.SimpleDialogTheme)
                .setPositiveButton(android.R.string.ok, null)
                .setTitle(getString(R.string.receive_books_over_usb))
                .setMessage(getString(R.string.ready_to_receive_usb))
                .create();
        d.show();
    }

    private void getFromUsbWithSaf() {
        mFileSearchState = new FileSearchState();

        if (SAFUtilities.hasPermissionToBloomDirectory(this)) {
            SAFUtilities.searchDirectoryForBooks(this, SAFUtilities.BloomDirectoryTreeUri, mBookSearchListener);
            return;
        }

        new AlertDialog.Builder(this, R.style.SimpleDialogTheme)
                .setTitle("Select Bloom directory")
                .setMessage("To receive books via USB, you will need to give Bloom Reader permission to use the \"Bloom\" folder at the root of Internal Storage. Touch \"USE THIS FOLDER\"")
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    Intent permissionIntent = SAFUtilities.getDirectoryPermissionIntent(SAFUtilities.BloomDirectoryUri);
                    mGetDirectoryToSearchForBooks.launch(permissionIntent);
                })
                .create()
                .show();
    }

    private void showLocationMessage() {
        // Report what we will do about location in analytics.
        LocationManager lm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        Location location = ReportAnalyticsTask.getLocation(lm);
        String locationString = "unknown";
        if (location != null) {
            locationString = "(" + location.getLatitude() + ", " + location.getLongitude()
                    + ") from provider " + location.getProvider()
                    + " with age " + ReportAnalyticsTask.locationAgeDays(location) + " days and accuracy "
                    + location.getAccuracy() + "m";
        }
        String message = "Bloom Reader will include location information in analytics reports. Current location is " + locationString;

        AlertDialog alertDialog = new AlertDialog.Builder(this, R.style.AlertDialogTheme).create();
        alertDialog.setTitle(getString(R.string.share_location));
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                (dialog, which) -> dialog.dismiss());
        alertDialog.show();
    }

    private void showLocationAnalyticsData() {
        // Check that the user is actually willing to give us permission to do what was just
        // requested.
        if (haveLocationPermission(this) && isLocationEnabled(this)) {
            showLocationMessage();
        } else if (!haveLocationPermission(this)) {
            // We have to be granted the permission before we can show the dialog. Usually we
            // only request it once, but now they ASKED to see the data that needs it!
            // Since they asked to see location data, I don't think we need to explain why
            // we need the necessary permission.
            showMessageOnLocationPermissionGranted = true;
            requestLocationPermission();
        } else {
            // Currently this won't show the dialog after they return from the system location
            // settings activity. There are some possible ways we might be able to make it
            // happen, such as using the onProviderEnabled callback above, but this is a debugging
            // tool for relatively technical people. They just need to issue the command again.
            requestTurnOnGps();
        }
    }

    // Has the user given permission for this app to get location data?
    public static boolean haveLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ;
    }

    // Has the user turned on the device service that makes location data possible?
    public static boolean isLocationEnabled(Context context) {
        // This is the currently recommended approach, but not usable before API 28.
        // LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        // return lm.isLocationEnabled();

        // This is a deprecated approach, but the only one I can find for API 21.
        int locationMode;
            try {
                locationMode = android.provider.Settings.Secure.getInt(context.getContentResolver(),
                        android.provider.Settings.Secure.LOCATION_MODE);

            } catch (android.provider.Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }

            return locationMode != android.provider.Settings.Secure.LOCATION_MODE_OFF;
    }

    public void requestLocationPermission() {
        // We don't really need GPS precision, but we do need to use GPS rather than depending
        // on the "network" location, which might be nonexistent or inaccurate in remote locations.
        String[] permissionsNeeded = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        ActivityCompat.requestPermissions(this, permissionsNeeded, 0);
    }

    // I think of this as requesting to turn on the GPS, but actually it brings up the system
    // settings for location, which may provide various options, including turning on some
    // location service other than GPS.
    public void requestTurnOnGps() {
        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    private void DisplaySimpleResource(String title, int fileResourceId) {
        // Linkify the message
        final SpannableString msg = new SpannableString(IOUtilities.InputStreamToString(getResources().openRawResource(fileResourceId)));
        Linkify.addLinks(msg, Linkify.ALL);

        final AlertDialog d = new AlertDialog.Builder(this, R.style.SimpleDialogTheme)
                .setPositiveButton(android.R.string.ok, null)
                .setTitle(title)
                .setMessage(msg)
                .create();
        d.show();

        // Make the textview clickable. Must be called after show()
        ((TextView)d.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    // invoked by click on bloomUrl textview in nav_header_main because it has onClick property
    public void bloomUrlClicked(View v) {
        Intent defaultBrowser = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
        defaultBrowser.setData(Uri.parse("https://bloomlibrary.org"));
        startActivity(defaultBrowser);
    }

    private void searchForBloomBooks() {
        if (osAllowsGeneralStorageAccess()) {
            if (haveLegacyStoragePermission(this)) {
                searchForBloomBooks_preAndroid11();
            } else {
                requestLegacyStoragePermission(STORAGE_PERMISSION_SEARCH);
            }
            return;
        }

        searchForBloomBooksUsingSaf();
    }

    // This is our "legacy" storage model which allowed us to gain
    // general file system access by user permission.
    // In Android 11, this became unavailable and we must use private storage
    // or gain access via Storage Access Framework (SAF).
    //
    // We could have returned true here if running on Android 11 and
    // the user still has legacy storage access because they did an upgrade
    // (see android:preserveLegacyExternalStorage="true" in AndroidManifest.xml).
    // However, that would mean users with Android 11 could have different experiences
    // and even the same user would have different experiences if he uninstalled/reinstalled.
    public static boolean osAllowsGeneralStorageAccess() {
        // Counter-intuitively, Build.VERSION.SDK_IN is the version of the Android system
        // we are running under, not the one we were built for. Q is Android 10, the last
        // version where the user could give us this permission.
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q;
    }

    public static boolean canUseGeneralStorageAccess() {
        if (!osAllowsGeneralStorageAccess())
            return false;
        return haveLegacyStoragePermission(BloomReaderApplication.getBloomApplicationContext());
    }

    private final BookSearchListener mBookSearchListener = new BookSearchListener() {
        @Override
        public void onNewBookOrShelf(File bookOrShelfFile, Uri bookOrShelfUri) {
            String filePath = bookOrShelfFile.getPath();
            // Don't add books found in BloomExternal to Bloom!
            // See https://issues.bloomlibrary.org/youtrack/issue/BL-7128.
            if (filePath.contains("/BloomExternal/") || filePath.contains(":BloomExternal/"))
                return;
            if (_bookCollection.getBookOrShelfByPath(filePath) == null) {
                Log.d("BookSearch", "Found " + filePath);
                if (importBook(bookOrShelfUri, false))
                    mFileSearchState.bloomdsAdded.add(bookOrShelfUri);
            }
        }

        @Override
        public void onNewBloomBundle(Uri bundleUri) {
            Log.d("BookSearch", "Found " + bundleUri.getPath());
            mFileSearchState.bundlesToAdd.add(bundleUri);
        }

        @Override
        public void onSearchComplete() {
            findViewById(R.id.searching_text).setVisibility(View.GONE);
            if (mFileSearchState.nothingAdded())
                Toast.makeText(MainActivity.this, R.string.no_books_added, Toast.LENGTH_SHORT).show();
            else {
                resetFileObserver();  // Prevents repeat notifications later
                // Multiple AsyncTask's will execute sequentially
                // https://developer.android.com/reference/android/os/AsyncTask#order-of-execution
                new ImportBundleTask(MainActivity.this).execute(mFileSearchState.bundlesToAddAsArray());
                new FileCleanupTask(MainActivity.this).execute(mFileSearchState.bloomdsAddedAsArray());
            }
        }
    };

    // This function can't be made to work in Android 11+, due to the new scoped storage rules.
    // However, devices running 10 or less can still use this more straightforward method.
    private void searchForBloomBooks_preAndroid11() {
        mFileSearchState = new FileSearchState();
        new BookFinderTask(this, mBookSearchListener).execute();
        findViewById(R.id.searching_text).setVisibility(View.VISIBLE);
    }

    private void searchForBloomBooksUsingSaf() {
        mFileSearchState = new FileSearchState();

        List<Uri> urisWithPermissions = SAFUtilities.getUrisWithPermissions(this);
        if (urisWithPermissions.size() > 0) {
            // TODO just search? search and then ask if nothing found? ask user if they want to specify another dir?
            // list directories we have already and ask if they need to add any?
        }

        Intent permissionIntent = SAFUtilities.getDirectoryPermissionIntent(null);

        //TODO
        // Intent permissionIntent = SAFUtilities.getDirectoryPermissionIntent("/BloomExternal");

//        Uri androidDir = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAndroid");
//        Uri bloomExternalDir = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABloomExternal");
//        Intent permissionIntent = SAFUtilities.getDirectoryPermissionIntent(bloomExternalDir);

        mGetDirectoryToSearchForBooks.launch(permissionIntent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(ALREADY_OPENED_FILE_FROM_INTENT_KEY, alreadyOpenedFileFromIntent);
    }

    private final ActivityResultLauncher<Intent> mGetBooksFromWiFi = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    String[] newBooks = result.getData().getStringArrayExtra(NEW_BOOKS);
                    if (newBooks != null) {
                        for (String bookPath : newBooks) {
                            _bookCollection.addBookIfNeeded(bookPath);
                        }
                        if (newBooks.length > 0) {
                            onNewOrUpdatedBook(newBooks[newBooks.length - 1]);
                        }
                    }
                }
            }
    );

    @SuppressLint("WrongConstant")
    private final ActivityResultLauncher<Intent> mGetDirectoryToSearchForBooks = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    // The result data contains a URI for the document or directory that the user selected.
                    if (data != null) {
                        Uri uri = data.getData();

                        // Persist our permission beyond device restart
                        final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);

                        SAFUtilities.searchDirectoryForBooks(this, uri, mBookSearchListener);
                    }
                }
            }
    );

    @SuppressLint("WrongConstant")
    private final ActivityResultLauncher<Intent> mGetExternalFilesDirectoryUri = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    // The result data contains a URI for the document or directory that the user selected.
                    if (data != null) {
                        Uri uri = data.getData();

                        // Persist our permission beyond device restart
                        final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);

                        reloadBookList();
                    }
                }
            }
    );
}
