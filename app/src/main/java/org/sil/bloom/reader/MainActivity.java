package org.sil.bloom.reader;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.sil.bloom.reader.WiFi.GetFromWiFiActivity;
import org.sil.bloom.reader.models.BookOrShelf;
import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.sil.bloom.reader.IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION;
import static org.sil.bloom.reader.models.BookOrShelf.BOOK_FILE_EXTENSION;


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

    private RecyclerView mBookRecyclerView;
    private BookListAdapter mBookListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //NB: if the build.gradle targetSdkVersion goes above 22, then the permission system changes and
            //the manifest's uses-permission stops working (there is a new system).
            Toast.makeText(this.getApplicationContext(), "Should Have External Write Permission", Toast.LENGTH_LONG);
            return;
        }

        try {
            _bookCollection= setupBookCollection();

            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            getSupportActionBar().setDisplayShowTitleEnabled(false);


            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                    this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            drawer.addDrawerListener(toggle);
            toggle.syncState();
            configureActionBar(toggle);

            NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
            navigationView.setNavigationItemSelectedListener(this);

            mBookRecyclerView = (RecyclerView) findViewById(R.id.book_list2);
            SetupCollectionListView(mBookRecyclerView);

            // If we were started by some external process, we need to process any file
            // we were given (a book or bundle)
            if (savedInstanceState != null)
                alreadyOpenedFileFromIntent = savedInstanceState.getBoolean(ALREADY_OPENED_FILE_FROM_INTENT_KEY, false);
            processIntentData();

            // Insert the build version and date into the appropriate control.
            // We have to find it indirectly through the navView's header or it won't be found
            // this early in the view construction.
            NavigationView navView = (NavigationView)findViewById(R.id.nav_view);
            TextView versionDate = (TextView)navView.getHeaderView(0).findViewById(R.id.versionDate);
            try {
                String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                Date buildDate = new Date(BuildConfig.TIMESTAMP);
                DateFormat df = new DateFormat();
                String date = df.format("dd MMM yyyy", buildDate).toString();
                versionDate.setText(versionName + ", " + date);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        catch(ExtStorageUnavailableException e){
            externalStorageUnavailable(e);
        }

        // Cleans up old-style thumbnails - could be removed someday after it's run on most devices with old-style thumbnails
        BookCollection.cleanUpOldThumbs(this);
    }

    // This is a hook to allow ShelfActivity to disable the navigation drawer and replace it
    // with a back button.
    protected void configureActionBar(ActionBarDrawerToggle toggle) {
    }

    // ShelfActivity does this differently.
    protected BookCollection setupBookCollection() throws ExtStorageUnavailableException {
        BloomReaderApplication.theOneBookCollection = new BookCollection();
        BloomReaderApplication.theOneBookCollection.init(this.getApplicationContext());
        return BloomReaderApplication.theOneBookCollection;
    }

    private void processIntentData() {
        Uri uri = getIntent().getData();
        if (uri == null || alreadyOpenedFileFromIntent)
            return;
        String nameOrPath = uri.getPath();
        // Content URI's do not use the actual filename in the "path"
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst())
                nameOrPath = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
        if (nameOrPath == null) // reported as crash on Play console
            return;
        if (nameOrPath.endsWith(BOOK_FILE_EXTENSION)) {
            importBook(uri);
        } else if (nameOrPath.endsWith(BLOOM_BUNDLE_FILE_EXTENSION)) {
            importBloomBundle(uri);
        } else {
            Log.e("Intents", "Couldn't figure out how to open URI: " + uri.toString());
        }
        alreadyOpenedFileFromIntent = true;
    }

    // If we were given a path to a book file,
    // we want copy it to where Bloom books live if it isn't already there,
    // make sure it is in our collection,
    // and then open the reader to view it.
    private void importBook(Uri bookUri){
        String newPath = _bookCollection.ensureBookIsInCollection(this, bookUri);
        if (newPath != null) {
            openBook(this, newPath);
        } else {
            Toast failToast = Toast.makeText(this, R.string.failed_book_import, Toast.LENGTH_LONG);
            failToast.show();
        }
    }

    private void importBloomBundle(Uri bloomBundleUri) {
        new ImportBundleTask(this).execute(bloomBundleUri);
    }

    // Called by ImportBundleTask with the list of new books and shelves
    public void bloomBundleImported(List<String> newBookPaths) {
        try {
            // Reinitialize completely to get the new state of things.
            _bookCollection.init(this.getApplicationContext());
        } catch (ExtStorageUnavailableException e) {
            Log.wtf("BloomReader", "Could not use external storage when reloading project!", e); // should NEVER happen
        }
        highlightItems(newBookPaths);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFilter();
        try {
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
            SharingManager.fileCleanup();
        }
        catch (ExtStorageUnavailableException e){
            externalStorageUnavailable(e);
        }
    }

    // a hook to allow ShelfActivity to set a real filter.
    // We need to set it to nothing here for when we return to the main activity from a shelf.
    protected void updateFilter() {
        _bookCollection.setFilter("");
    }

    private void externalStorageUnavailable(ExtStorageUnavailableException e){
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
        if (book == null)
            return;

        int bookPosition = mBookListAdapter.highlightItem(book);
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
        new SharingManager(this).shareBook(book);
    }

    private void shareShelf(BookOrShelf shelf){
        List<BookOrShelf> booksAndShelves = _bookCollection.getAllBooksWithinShelf(shelf);
        new SharingManager(this).shareShelf(shelf, booksAndShelves);
    }

    private void deleteBookOrShelf(){
        BookOrShelf bookOrShelf = mBookListAdapter.getSelectedItem();
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
        new AlertDialog.Builder(this).setMessage(message)
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
        new AlertDialog.Builder(this).setMessage(getString(R.string.deleteExplanationBook, book.name))
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
        final AppCompatActivity activity = this;
        listView.setLayoutManager(new LinearLayoutManager(this));
        mBookListAdapter = new BookListAdapter(_bookCollection, this);
        listView.setAdapter(mBookListAdapter);
    }

    @Override
    public void onBookClick(BookOrShelf bookOrShelf){
        openBook(this, bookOrShelf.path);
    }

    @Override
    public boolean onBookLongClick(){
        contextualActionBarMode = startActionMode(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    mode.getMenuInflater().inflate(R.menu.book_item_menu, menu);
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
                mBookListAdapter.clearSelection();
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
            // Deleted (probably by some other process). We consider this a corner case
            // unworthy of creating message strings that must be localized, so just
            // clean it up. (Since it already doesn't exist, deleteFromDevice just
            // removes it from the collection.)
            _bookCollection.deleteFromDevice(_bookCollection.getBookByPath(path));
            mBookListAdapter.notifyDataSetChanged();
            return;
        }
        BookOrShelf bookOrShelf = _bookCollection.getBookByPath(path);
        if (bookOrShelf.isShelf()) {
            Intent intent = new Intent(context, ShelfActivity.class);
            intent.putExtra("filter", bookOrShelf.shelfId);
            intent.putExtra("label", bookOrShelf.name); // Or get it from the appropriate ws of label
            intent.putExtra("background", bookOrShelf.backgroundColor);
            context.startActivity(intent);
        } else {
            Intent intent = new Intent(context, ReaderActivity.class);
            intent.setData(Uri.parse(path));
            intent.putExtra("brandingProjectName", bookOrShelf.brandingProjectName);
            context.startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
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
            case R.id.action_settings:
                return true;
            case R.id.delete:
                Toast.makeText(this.getApplicationContext(), "Would delete", Toast.LENGTH_LONG);
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
            case R.id.nav_share_app:
                ShareDialogFragment shareDialogFragment = new ShareDialogFragment();
                shareDialogFragment.show(getFragmentManager(), ShareDialogFragment.SHARE_DIALOG_FRAGMENT_TAG);
                break;
            case R.id.nav_share_books:
                ShareBooksDialogFragment shareBooksDialogFragment = new ShareBooksDialogFragment();
                shareBooksDialogFragment.show(getFragmentManager(), ShareBooksDialogFragment.SHARE_BOOKS_DIALOG_FRAGMENT_TAG);
                break;
            case R.id.nav_release_notes:
                DisplaySimpleResource(getString(R.string.release_notes), R.raw.release_notes);
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

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
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

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(ALREADY_OPENED_FILE_FROM_INTENT_KEY, alreadyOpenedFileFromIntent);
    }
}
