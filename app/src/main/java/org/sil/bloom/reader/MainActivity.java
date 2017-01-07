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
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.sil.bloom.reader.models.Book;
import org.sil.bloom.reader.models.BookCollection;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private BookCollection _bookCollection = new BookCollection();
    private ListView mListView;
    public android.view.ActionMode contextualActionBarMode;

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
        Uri data = getIntent().getData();
        if(data != null && data.getPath().toLowerCase().endsWith(".bloom")) {
            String newpath = _bookCollection.ensureBookIsInCollection(data.getPath());
            openBook(this, newpath);
        }
    }

    private void closeContextualActionBar() {
        contextualActionBarMode.finish();
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

        ArrayAdapter adapter = new ArrayAdapter(this, R.layout.book_list_content, R.id.title, _bookCollection.getBooks());
        adapter.setNotifyOnChange(true);
        listView.setAdapter(adapter);

        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);


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

                mListView.setOnItemClickListener(null);
                contextualActionBarMode= activity.startActionMode(mActionModeCallback);

                return true;
            }
        });

    }

    private void openBook(Context context, String path) {
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

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_catalog) {

        } else if (id == R.id.nav_share) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
