package org.sil.bloom.reader;

import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toolbar;

import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

/**
 * Created by Thomson on 10/20/2017.
 * Showing a bookshelf is much like our main activity, which also shows a list of books (and shelves--
 * something that just might come to this activity one day). For now we are using the same layout,
 * with the shelf label block hidden ('gone' so it doesn't even take up space) in the main activity.
 * There are just a few things we need to do differently.
 * It may at some point be worth refactoring to extract a common base class, but for now, I prefer
 * to see all the logic in the class that primarily uses it and for which it was originally written,
 * MainActivity itself. This will also tend to facilitate git comparisons.
 */
public class ShelfActivity extends MainActivity {

    private String filter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This is one half of configuring the action bar to have an up (back) arrow instead of
        // the usual hamburger menu. The other half is the override of configureActionBar().
        // I would like to put this line in that method but for some unknown reason that doesn't
        // work...we get no control at all in the hamburger position.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        filter = getIntent().getStringExtra("filter");

        // Turn on the bookshelf label (hidden in MainActivity) and initialize it with the data
        // passed through our intent.
        LinearLayout label = (LinearLayout) findViewById(R.id.shelf_label_layout);
        label.setVisibility(View.VISIBLE);
        label.setBackgroundColor(Color.parseColor("#" + getIntent().getStringExtra("background")));
        TextView labelText = (TextView) findViewById(R.id.shelfName);
        labelText.setText(getIntent().getStringExtra("label"));

        Button backButton = (Button) findViewById(R.id.back_button);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    // In the shelf view the action bar's menu is replaced with a back button.
    // It's possible we could achieve this in another way, for example, by initializing the
    // shelf view with a layout that doesn't have a navigation bar at all. But as the views
    // are so similar, I like having them share the same layout.
    @Override
    protected void configureActionBar(ActionBarDrawerToggle toggle) {
        toggle.setDrawerIndicatorEnabled(false);
        toggle.setToolbarNavigationClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    @Override
    protected void updateFilter() {
        _bookCollection.setFilter(filter);
    }

    @Override
    protected BookCollection setupBookCollection() throws ExtStorageUnavailableException {
        // Don't need (or want) to initialize it, assume main activity already did.
        // Review: is there any way the system might start BR with this as the first activity?
        return BloomReaderApplication.theOneBookCollection;
    }
}
