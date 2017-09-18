package org.sil.bloom.reader;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.sil.bloom.reader.WiFi.GetFromWiFiActivity;
import org.sil.bloom.reader.models.Book;
import org.sil.bloom.reader.models.BookCollection;

import java.io.File;
import java.util.Date;


public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static final String NEW_BOOKS = "newBooks";
    private BookCollection _bookCollection = new BookCollection();
    private ListView mListView;
    public android.view.ActionMode contextualActionBarMode;
    private static boolean sSkipNextNewFileSound;
    ArrayAdapter mListAdapter;

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

        _bookCollection.init(this.getApplicationContext());


        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayShowTitleEnabled(false);


        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        mListView = (ListView) findViewById(R.id.book_list2);
        SetupCollectionListView(mListView);

        // If we were started by some external process and given a path to a book file,
        // we want copy it to where Bloom books live if it isn't already there,
        // make sure it is in our collection,
        // and then open the reader to view it.
        importBookIfAttached(getIntent());

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

    private void importBookIfAttached(Intent intent){
        Uri bookUri = getIntent().getData();
        if(bookUri == null)
            return;
        String newpath = _bookCollection.ensureBookIsInCollection(this, bookUri);
        if(newpath != null) {
            openBook(this, newpath);
        } else{
            Toast failToast = Toast.makeText(this, R.string.failed_book_import, Toast.LENGTH_LONG);
            failToast.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // we will get notification through onNewOrUpdatedBook if Bloom pushes a new or updated
        // book to our directory using MTP.
        startObserving();
        // And right now we will trigger the notification if anyone or anything has changed a
        // book in our folder while we were paused.
        notifyIfNewFileChanges();
        String bookToHighlight = ((BloomReaderApplication) this.getApplication()).getBookToHighlight();
        if (bookToHighlight != null) {
            updateForNewBook(bookToHighlight);
            ((BloomReaderApplication) this.getApplication()).setBookToHighlight(null);
        } else {
            // We could have gotten a new book while the app was not in the foreground
            updateDisplay();
        }
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
        Book book = _bookCollection.addBookIfNeeded(filePath);
        refreshList(book);
        if (sSkipNextNewFileSound) {
            sSkipNextNewFileSound = false;
        }
        else {
            playSoundFile(R.raw.bookarrival);
        }
        Toast.makeText(MainActivity.this, book.name + " added or updated", Toast.LENGTH_SHORT).show();
    }

    private void updateDisplay() {
        refreshList(null);
    }

    private void refreshList(Book book) {
        mListView.invalidateViews();
        if (book != null) {
            int bookIndex = _bookCollection.indexOf(book);
            mListView.smoothScrollToPosition(bookIndex);
            mListView.setItemChecked(bookIndex, true);
        }
    }

    private void closeContextualActionBar() {
        contextualActionBarMode.finish();
    }

    private void shareBook(){
        int position = mListView.getCheckedItemPosition();
        Book book = _bookCollection.get(position);
        File bookFile = new File(book.path);
        Uri fileUri = FileProvider.getUriForFile(this, "org.sil.bloom.reader.fileprovider", bookFile);

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.setType("*/*");

        startActivity(Intent.createChooser(shareIntent, getResources().getString(R.string.share) + " " + book.name));
    }

    public void DeleteBook() {
        int position = mListView.getCheckedItemPosition();
        final Book book = _bookCollection.get(position);

        AlertDialog x = new AlertDialog.Builder(this).setMessage(getString(R.string.deleteExplanation))
                .setTitle(getString(R.string.deleteConfirmation))
                .setPositiveButton(getString(R.string.deleteConfirmButton), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.i("BloomReader", "DeleteBook "+ book.toString());
                        _bookCollection.deleteFromDevice(book);
                        closeContextualActionBar();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .show();
    }

    private void SetupCollectionListView(final ListView listView) {
        final AppCompatActivity activity = this;

        mListAdapter = new ArrayAdapter(this, R.layout.book_list_content, R.id.title, _bookCollection.getBooks());
        mListAdapter.setNotifyOnChange(true);
        listView.setAdapter(mListAdapter);

        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
                public void onItemClick(AdapterView<?> adapter, View v, int position, long arg3) {
                    Context context = v.getContext();
                    openBook(context, _bookCollection.get(position).path);
                }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            private android.view.ActionMode.Callback mActionModeCallback = new android.view.ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(android.view.ActionMode mode, Menu menu) {
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
                            shareBook();
                            mode.finish();
                            return true;
                        case R.id.delete:
                            DeleteBook();
                            return true;
                        default:
                            return false;
                    }
                }

                @Override
                public void onDestroyActionMode(android.view.ActionMode mode) {
                    contextualActionBarMode = null;
                    mListView.setItemChecked(mListView.getCheckedItemPosition(), false);
                    //mListView.setSelection(-1);
                }
            };

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if(contextualActionBarMode != null)
                    return false;
                mListView.setSelected(true);
                mListView.setSelection(position);
                mListView.setItemChecked(position, true);

                contextualActionBarMode= activity.startActionMode(mActionModeCallback);

                return true;
            }
        });

    }

    private void openBook(Context context, String path) {
        if (!new File(path).exists()) {
            // Deleted (probably by some other process). We consider this a corner case
            // unworthy of creating message strings that must be localized, so just
            // clean it up. (Since it already doesn't exist, deleteFromDevice just
            // removes it from the collection.)
            _bookCollection.deleteFromDevice(_bookCollection.getBookByPath(path));
            // JT: without this an exception is thrown saying we should have called it.
            // I cannot figure out why other things that change the list...especially our own
            // delete command...do not need this.
            mListAdapter.notifyDataSetChanged();
            return;
        }
        Intent intent = new Intent(context, ReaderActivity.class);
        intent.setData(Uri.parse(path));
        context.startActivity(intent);
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

        final AlertDialog d = new AlertDialog.Builder(this)
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
}
