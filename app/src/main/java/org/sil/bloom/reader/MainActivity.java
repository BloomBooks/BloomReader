package org.sil.bloom.reader;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import com.google.android.material.navigation.NavigationView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.segment.analytics.Analytics;
import com.segment.analytics.Properties;

import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.BookOrShelf;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;
import org.sil.bloom.reader.wifi.GetFromWiFiActivity;

import java.io.File;
import java.io.IOException;
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
    private boolean onResumeIsWaitingForStoragePermission = false;
    private boolean showMessageOnLocationPermissionGranted = false;

    private RecyclerView mBookRecyclerView;
    BookListAdapter mBookListAdapter;       // accessed by InitializeLibraryTask
    // Keeps track of the state of an ongoing File Search
    private FileSearchState fileSearchState;

    // Dynamically created/destroyed progress bar and text view used during initial loading.
    ProgressBar mLoadingProgressBar;       // accessed by InitializeLibraryTask
    TextView mLoadingTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (haveStoragePermission(this)) {
            // The only way we would have storage permission, since we no longer ask for it, is
            // if we're migrating from an earlier version. So go ahead and do it. Before we create
            // the main activity, so creating it will see any migrated books.
            migrateLegacyData();
        }
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
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dont_allow),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                wasDontAllowPressed[0] = true;
                                dialog.dismiss();
                            }
                        });
                alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        // We want this to happen even if the user dismisses the dialog
                        // by tapping outside it rather than by tapping OK.
                        // But not if they actually tapped "don't allow"
                        if (!wasDontAllowPressed[0]) {
                            requestLocationPermission();
                        }
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
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                doTurnOn[0] = true;
                                dialog.dismiss();
                                // It doesn't seem to work to requestTurnOnGps() here.
                                // I think the problem is that the switch from the dialog
                                // activity back to this caused by dismiss() beats the switch
                                // to the system settings dialog.
                            }
                        });
                alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
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
                    }
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
                public void onLocationChanged(Location location) {
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                    // Enhance: seems to get called when the user turns it on, even in the
                    // background. Could possibly be used to show the dialog, if the user
                    // was put into the settings screen as a result of asking to check
                    // analytics location data. But things are complicated enough already!
                }

                @Override
                public void onProviderDisabled(String provider) {
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

    public static boolean haveStoragePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)  == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        // This override is the way we get results of ActivityCompat.requestPermissions().
        // Currently our only call to requestPermissions is asking permission to use location services.
        //The test here just confirms that...it is really left over from when we also needed external storage.
        if (permissions.length > 0 && permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // We're getting an answer about location
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
        }
    }

    private void migrateLegacyData() {
        File oldBloomDir = Environment.getExternalStoragePublicDirectory("Bloom");
        File newBloomDir = BookCollection.getLocalBooksDirectory();
        try {
            if (!oldBloomDir.exists()) {
                return; // nothing to migrate
            }
            // It's slightly wasteful to do this if we already have. However, in the release build,
            // we delete the directory after copying it, so it will only happen once. Someone who
            // is messing with alpha or beta might like to see any new books they fetch with the
            // old release build.
            boolean testMode = BuildConfig.DEBUG || BuildConfig.FLAVOR.equals("alpha") || BuildConfig.FLAVOR.equals("beta");
            for (File f : oldBloomDir.listFiles()) {
                String fileName = f.getName();
                if (fileName == ".thumbs") {
                    continue; // this is a directory, and the data can be rebuilt, so save time by not copying
                }
                File dest = new File(newBloomDir, fileName);
                if (dest.exists()) {
                    continue; // Don't re-copy, and especially don't overwrite a possibly newer version.
                }
                if (testMode) {
                    IOUtilities.copyFile(f.getPath(), dest.getPath());
                } else {
                    f.renameTo(dest);
                }
            }
            if (!testMode) {
                IOUtilities.deleteFileOrDirectory(oldBloomDir);
            }
        }
        catch (SecurityException e) {
            Log.e("migrateLegacyData", e.getMessage());
        }

        // How to get the old BloomExternal directory.
//            if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
//            throw new ExtStorageUnavailableException();
        //
        // and the old external one
//    File remoteStorageDir = IOUtilities.removablePublicStorageRoot(context);
//    File remoteBooksDir = new File(remoteStorageDir, "BloomExternal");
    }

    private void createMainActivity(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);
        BloomReaderApplication.setupVersionUpdateInfo(this); // may use analytics, so must run after it is set up.

        _bookCollection = setupBookCollection();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayShowTitleEnabled(false);


        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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
        TextView versionDate = (TextView) navigationView.getHeaderView(0).findViewById(R.id.versionDate);
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

        if (onResumeIsWaitingForStoragePermission)
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
        String nameOrPath = uri.getPath();
        // Content URI's do not use the actual filename in the "path"
        if (uri.getScheme().equals("content")) {
            ContentResolver contentResolver = getContentResolver();
            if (contentResolver == null) // Play console showed us this could be null somehow
                return;
            Cursor cursor;
            try {
                cursor = contentResolver.query(uri, null, null, null, null);
            } catch (SecurityException se) {
                // Not sure how this happens, but we see it on the Play Console.
                // Perhaps someone has chosen Bloom Reader to try to process an intent we shouldn't be trying to handle?
                return;
            }
            if (cursor != null) {
                if (cursor.moveToFirst())
                    nameOrPath = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                cursor.close();
            }
        }
        if (nameOrPath == null) // reported as crash on Play console
            return;
        if (nameOrPath.endsWith(BOOK_FILE_EXTENSION) ||
                nameOrPath.endsWith(BOOK_FILE_EXTENSION + ENCODED_FILE_EXTENSION)) {
            importBook(uri, IOUtilities.getFilename(nameOrPath), true);
        } else if (nameOrPath.endsWith(BLOOM_BUNDLE_FILE_EXTENSION)) {
            importBloomBundle(uri);
        } else {
            Log.e("Intents", "Couldn't figure out how to open URI: " + uri.toString());
            return; // keep BR from saying we opened it, when we didn't!
        }
        alreadyOpenedFileFromIntent = true;
    }

    // Copy a book to our Bloom folder and add it to the library.
    // If we're importingOneFile (ie not doing a FileSearch of the device),
    // we'll go ahead and open the book and do file cleanup.
    // Return value indicates success.
    private boolean importBook(Uri bookUri, String filename, boolean importingOneFile) {
        String newPath = null;
        newPath = _bookCollection.ensureBookIsInCollection(this, bookUri, filename);
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

    // Called by ImportBundleTask with the list of new books and shelves
    public void bloomBundleImported(List<String> newBookPaths) {
            // Reinitialize completely to get the new state of things.
            _bookCollection.init(this, null);
            // Don't highlight the set of new books, just update the list displayed. (BL-8808)
            mBookListAdapter.notifyDataSetChanged();
            resetFileObserver(); // Prevent duplicate notifications
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (haveStoragePermission(this) && !onResumeIsWaitingForStoragePermission)
            resumeMainActivity();
        else
            onResumeIsWaitingForStoragePermission = true;
    }

    private void resumeMainActivity() {
        onResumeIsWaitingForStoragePermission = false;
        updateFilter();
        // we will get notification through onNewOrUpdatedBook if Bloom pushes a new or updated
        // book to our directory using MTP.
        startObserving();
        // And right now we will trigger the notification if anyone or anything has changed a
        // book in our folder while we were paused.
        String booksDir = BookCollection.getLocalBooksDirectory().getPath();
        notifyIfNewFileChanges(booksDir);
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

    void externalStorageUnavailable(ExtStorageUnavailableException e){      // conditionally called by InitializeLibraryTask.onPostExecute()
        Toast failToast = Toast.makeText(this, getString(R.string.external_storage_unavailable), Toast.LENGTH_LONG);
        failToast.show();
        finish();
    }

    @Override
    protected void onPause() {
        stopObserving();

        super.onPause();
    }

    @Override
    protected void onNewOrUpdatedBook(String filePath) {
        final String filePathLocal = filePath;
        runOnUiThread(new Runnable(){
            @Override
            public void run() {
                updateForNewBook(filePathLocal);
            }
        });
    }

    public static void skipNextNewFileSound() {
        sSkipNextNewFileSound = true;
    }

    private void updateForNewBook(String filePath) {
        BookOrShelf book = _bookCollection.addBookIfNeeded(filePath);
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
                .setPositiveButton(R.string.deleteConfirmButton, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        Log.i("BloomReader", "DeleteShelf " + shelf.toString());
                        for(BookOrShelf b : booksAndShelves)
                            _bookCollection.deleteFromDevice(b);
                        mBookListAdapter.notifyDataSetChanged();
                        closeContextualActionBar();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    private void deleteBook(final BookOrShelf book) {
        new AlertDialog.Builder(this, R.style.SimpleDialogTheme).setMessage(getString(R.string.deleteExplanationBook, book.name))
                .setTitle(getString(R.string.deleteConfirmation))
                .setPositiveButton(getString(R.string.deleteConfirmButton), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.i("BloomReader", "DeleteBook "+ book.toString());
                        _bookCollection.deleteFromDevice(book);
                        mBookListAdapter.notifyDataSetChanged();
                        closeContextualActionBar();
                        dialog.dismiss();
                    }
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
        openBook(this, bookOrShelf.path);
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
                switch(item.getItemId()) {
                    case R.id.share:
                        shareBookOrShelf();
                        mode.finish();
                        return true;
                    case R.id.delete:
                        deleteBookOrShelf();
                        return true;
                    default:
                        return false;
                }
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
        if (!new File(path).exists()) {
            // Possibly deleted - possibly on an sd card that got removed
            Toast.makeText(this, getString(R.string.missing_book, BookOrShelf.getNameFromPath(path)), Toast.LENGTH_LONG).show();
            // Remove the book from the collection
            _bookCollection.deleteFromDevice(_bookCollection.getBookOrShelfByPath(path));
            mBookListAdapter.notifyDataSetChanged();
            return;
        }
        BookOrShelf bookOrShelf = _bookCollection.getBookOrShelfByPath(path);
        if (bookOrShelf == null) {
            // Play console shows this can happen somehow.
            // Maybe we when fix the concurrency issues, this goes away, too.
            return;
        }
        if (bookOrShelf.isShelf()) {
            Intent intent = new Intent(context, ShelfActivity.class);
            intent.putExtra("filter", bookOrShelf.shelfId);
            intent.putExtra("label", bookOrShelf.name); // Or get it from the appropriate ws of label
            intent.putExtra("background", bookOrShelf.backgroundColor);
            context.startActivity(intent);
        } else {
            Intent intent = new Intent(context, ReaderActivity.class);
            intent.putExtra("bookPath", path);
            intent.putExtra("brandingProjectName", bookOrShelf.brandingProjectName);
            context.startActivity(intent);
        }
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

        //noinspection SimplifiableIfStatement
        switch(id) {
            case R.id.delete:
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    static final int DOWNLOAD_BOOKS_REQUEST = 1;

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

//        if (id == R.id.nav_catalog) {
//
//        } else if (id == R.id.nav_share) {
//
//        }
//        else
        switch (id) {
            case R.id.nav_get_wifi:
                Intent intent = new Intent(this, GetFromWiFiActivity.class);
                this.startActivityForResult(intent, DOWNLOAD_BOOKS_REQUEST);
                break;
            case R.id.nav_get_usb:
                final AlertDialog d = new AlertDialog.Builder(this, R.style.SimpleDialogTheme)
                        .setPositiveButton(android.R.string.ok, null)
                        .setTitle(getString(R.string.receive_books_over_usb))
                        .setMessage(getString(R.string.ready_to_receive_usb))
                        .create();
                d.show();
                break;
            case R.id.nav_share_app:
                ShareDialogFragment shareDialogFragment = new ShareDialogFragment();
                shareDialogFragment.show(getFragmentManager(), ShareDialogFragment.SHARE_DIALOG_FRAGMENT_TAG);
                break;
            case R.id.nav_share_books:
                ShareAllBooksDialogFragment shareBooksDialogFragment = new ShareAllBooksDialogFragment();
                shareBooksDialogFragment.show(getSupportFragmentManager(), ShareAllBooksDialogFragment.SHARE_BOOKS_DIALOG_FRAGMENT_TAG);
                break;
            case R.id.nav_release_notes:
                DisplaySimpleResource(getString(R.string.release_notes), R.raw.release_notes);
                break;
//            case R.id.nav_search_for_bundles:
//                searchForBloomBooks();
//                break;
            case R.id.nav_test_location_analytics:
                showLocationAnalyticsData();
                break;
            case R.id.about_reader:
                DisplaySimpleResource(getString(R.string.about_bloom_reader), R.raw.about_reader);
                break;
            case R.id.about_bloom:
                DisplaySimpleResource(getString(R.string.about_bloom), R.raw.about_bloom);
                break;
            case R.id.about_sil:
                DisplaySimpleResource(getString(R.string.about_sil), R.raw.about_sil);
                break;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
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
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
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
        // This is the currently recommended approach, but not useable before API 28.
        // LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        // return lm.isLocationEnabled();

        // This is a deprecated approach, but the only one I can find for API 21.
        int locationMode = 0;
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
        // for some reason, it doesn't work with just bloomlibrary.org. Seems to thing a URL
        // must have http://www. Fails to resolve activity.
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.bloomlibrary.org"));
        if (browserIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(browserIntent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == DOWNLOAD_BOOKS_REQUEST && resultCode == RESULT_OK) {
            String[] newBooks = data.getStringArrayExtra(NEW_BOOKS);
            for (String bookPath : newBooks) {
                _bookCollection.addBookIfNeeded(bookPath);
            }
            if (newBooks.length > 0) {
                onNewOrUpdatedBook(newBooks[newBooks.length - 1]);
            }
        }
    }

    // This function can't be made to work in Android 11, due to the new scoped storage rules.
    // Keeping the code as we may implement something similar where the user selects a root folder
    // to search in (and thus implicitly gives us permission to access it, albeit through
    // SAF (Storage Access Framework) rather than the File system.
//    private void searchForBloomBooks() {
//        fileSearchState = new FileSearchState();
//        BookFinderTask.BookSearchListener bookSearchListener = new BookFinderTask.BookSearchListener() {
//            @Override
//            public void onNewBookOrShelf(File bookOrShelfFile) {
//                String filePath = bookOrShelfFile.getPath();
//                // Don't add books found in BloomExternal to Bloom!
//                // See https://issues.bloomlibrary.org/youtrack/issue/BL-7128.
//                if (filePath.contains("/BloomExternal/"))
//                    return;
//                if (_bookCollection.getBookOrShelfByPath(filePath) == null) {
//                    Log.d("BookSearch", "Found " + filePath);
//                    Uri bookUri = Uri.fromFile(bookOrShelfFile);
//                    if (importBook(bookUri, bookOrShelfFile.getName(),false))
//                        fileSearchState.bloomdsAdded.add(bookUri);
//                }
//            }
//
//            @Override
//            public void onNewBloomBundle(File bundleFile) {
//                Log.d("BookSearch", "Found " + bundleFile.getPath());
//                fileSearchState.bundlesToAdd.add(Uri.fromFile(bundleFile));
//            }
//
//            @Override
//            public void onSearchComplete() {
//                findViewById(R.id.searching_text).setVisibility(View.GONE);
//                if (fileSearchState.nothingAdded())
//                    Toast.makeText(MainActivity.this, R.string.no_books_added, Toast.LENGTH_SHORT).show();
//                else {
//                    resetFileObserver();  // Prevents repeat notifications later
//                    // Multiple AsyncTask's will execute sequentially
//                    // https://developer.android.com/reference/android/os/AsyncTask#order-of-execution
//                    new ImportBundleTask(MainActivity.this).execute(fileSearchState.bundlesToAddAsArray());
//                    new FileCleanupTask(MainActivity.this).execute(fileSearchState.bloomdsAddedAsArray());
//                }
//            }
//        };
//        new BookFinderTask(this, bookSearchListener).execute();
//        findViewById(R.id.searching_text).setVisibility(View.VISIBLE);
//    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(ALREADY_OPENED_FILE_FROM_INTENT_KEY, alreadyOpenedFileFromIntent);
    }
}
