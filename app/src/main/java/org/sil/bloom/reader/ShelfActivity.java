package org.sil.bloom.reader;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.appcompat.app.ActionBarDrawerToggle;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

/**
 * Created by Thomson on 10/20/2017.
 * Showing a bookshelf is much like our main activity, which also shows a list of books (and shelves--
 * something that just might come to this activity one day). For now we are using the same layout,
 * with the action bar changed to show the shelf icon, title and background and a back button instead
 * of the menu.
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

        // Initialize the bookshelf label (empty in MainActivity) with the data
        // passed through our intent.
        View toolbar = findViewById(R.id.toolbar);
        final int background = Color.parseColor("#" + getIntent().getStringExtra("background"));
        toolbar.setBackgroundColor(background);
        TextView labelText = findViewById(R.id.shelfName);
        labelText.setText(getIntent().getStringExtra("label"));
        ImageView bloomIcon = findViewById(R.id.bloom_icon);
        // replace the main bloom icon with the bookshelf one.
        bloomIcon.setImageResource(R.drawable.bookshelf);
        bloomIcon.setPadding(0,0, 10,0);

        // The color chosen for the bookshelf may not contrast well with the default white
        // color of text and the back arrow and the default black color of the bookshelf icon.
        // Change them all to black or white, whichever gives better contrast.
        int forecolor = pickTextColorBasedOnBgColor(background, Color.WHITE, Color.BLACK);
        labelText.setTextColor(forecolor);
        // This bit of magic from https://stackoverflow.com/questions/26788464/how-to-change-color-of-the-back-arrow-in-the-new-material-theme
        // makes the the back arrow use the contrasting foreground color
        Drawable upArrow = ((androidx.appcompat.widget.Toolbar)toolbar).getNavigationIcon();
        upArrow.setColorFilter(forecolor, PorterDuff.Mode.SRC_ATOP);

        // And this bit, from https://stackoverflow.com/questions/1309629/how-to-change-colors-of-a-drawable-in-android,
        // switches the color of the bookshelf icon.
        bloomIcon.setColorFilter(forecolor);
    }

    // official W3C recommendation for deciding whether a light or dark color will contrast best
    // with the given background color. The right one to use is returned.
    // Based on https://stackoverflow.com/questions/3942878/how-to-decide-font-color-in-white-or-black-depending-on-background-color
    int pickTextColorBasedOnBgColor(int bgColor, int lightColor, int darkColor) {
        int r = (bgColor >> 16) & 0xff;
        int g = (bgColor >> 8) & 0xff;
        int b = bgColor & 0xff;
        double[] uicolors = new double[] {r / 255, g / 255, b / 255};
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double col = uicolors[i];
            if (col < 0.03928)
                c[i] = col/12.92;
            else
                c[i] = Math.pow((col + 0.055)/1.055, 2.4);
        }

        double luminance = (0.2126 * c[0]) + (0.7152 * c[1]) + (0.0722 * c[2]);
        return (luminance > 0.179) ? darkColor : lightColor;
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
        if (BloomReaderApplication.theOneBookCollection == null) {
            // Presume the OS threw away and re-created the BloomReaderApplication while we
            // weren't looking. We need to do the full job of making one.
            return super.setupBookCollection();
        }
        // normally we just want to use the one the main activity made.
        return BloomReaderApplication.theOneBookCollection;
    }
}
