package org.sil.bloom.reader;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.BookOrShelf;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the RecyclerView used by MainActivity and ShelfActivity
 *
 */

public class BookListAdapter extends RecyclerView.Adapter<BookListAdapter.ViewHolder> implements View.OnClickListener, View.OnLongClickListener {

    private BookCollection bookCollection;
    private BookClickListener bookClickListener;
    private BookOrShelf selectedItem;
    private boolean inHighlightedState = false;

    public BookListAdapter(BookCollection bookCollection, BookClickListener bookClickListener){
        this.bookCollection = bookCollection;
        this.bookClickListener = bookClickListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType){
        LinearLayout bookLayout = (LinearLayout) LayoutInflater
                                                    .from(parent.getContext())
                                                    .inflate(R.layout.book_list_content, parent, false);
        bookLayout.setOnClickListener(this);
        bookLayout.setOnLongClickListener(this);
        return new ViewHolder(bookLayout);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position){
        holder.bookOrShelf = bookCollection.get(position);
        holder.linearLayout.setTag(holder.bookOrShelf);
        String title = TextUtils.isEmpty(holder.bookOrShelf.title) ? holder.bookOrShelf.name : holder.bookOrShelf.title;
        holder.bookNameView.setText(title);
        new SetBookListItemViewExtrasTask(holder).setExtras(); // Sets the thumbnail and speaker icon
        AdjustItemAppearance(holder);
    }


    private void AdjustItemAppearance(ViewHolder holder){
        if (holder.bookOrShelf == selectedItem)
            holder.linearLayout.setBackgroundColor(ContextCompat.getColor(holder.getContext(), R.color.colorAccent));
        else if (holder.bookOrShelf.highlighted)
            holder.linearLayout.setBackgroundColor(ContextCompat.getColor(holder.getContext(), R.color.new_book_highlight));
        else
            holder.linearLayout.setBackgroundColor(Color.WHITE);
        if ("loadExternalFiles".equals(holder.bookOrShelf.specialBehavior) || "importOldBloomFolder".equals(holder.bookOrShelf.specialBehavior)) {
            // I'm not sure this is the best way to do this. Maybe we should use an entirely different layout?
            // I'm not getting the exact appearance JohnH put in the doc...the image he gave me seems to have
            // only two 'contacts' instead of three, and it's bigger than the mockup. It may also be higher than
            // he wants; not sure of the implications of making this smaller than the others. But it may
            // be close enough?
            final Resources resources = holder.getContext().getResources();
            // Converts 'dp' measurements into actual pixels
            int hMargin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    16,
                    resources.getDisplayMetrics()
            );
            int topMargin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    4,
                    resources.getDisplayMetrics()
            );

            // Make a red border and make the text red.
            holder.linearLayout.setBackground(resources.getDrawable(R.drawable.red_border));
            holder.bookNameView.setTextColor(resources.getColor(R.color.colorBloomRed));

            // put some margin on the outermost layout so the border isn't hard up against the sides of the device
            // or the top of the window, but aligned with other books and shelves.
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.linearLayout.getLayoutParams();
            params.setMargins(hMargin, topMargin, hMargin,0);
            holder.linearLayout.setLayoutParams(params);

            // Useful in debugging, to show the space in which the image is centered.
            //holder.imageView.setBackgroundColor(ContextCompat.getColor(holder.getContext(), R.color.new_book_highlight));

            // Remove the left/start margin. The margin it normally has is applied above to the outer layout to
            // move the border in.
            LinearLayout.LayoutParams params1 = (LinearLayout.LayoutParams) holder.imageView.getLayoutParams();
            params1.setMargins(0, 0, 0,0);
            params1.setMarginStart(0);
            holder.imageView.setLayoutParams(params1);

            // Increase the top margin of the book name view to center-align it.
            int textTopMargin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    20,
                    resources.getDisplayMetrics()
            );
            params = (ViewGroup.MarginLayoutParams) holder.bookNameView.getLayoutParams();
            params.setMargins(0, textTopMargin, 0,0);
            holder.bookNameView.setLayoutParams(params);
        }
    }

    @Override
    public int getItemCount(){
        return bookCollection.size();
    }

    @Override
    public void onClick(View view) {
        if (selectedItem != null) {
            clearSelection();
            bookClickListener.onClearBookSelection();
        }
        else {
            bookClickListener.onBookClick((BookOrShelf) view.getTag());
        }

        clearHighlight();
    }

    @Override
    public boolean onLongClick(View view) {
        BookOrShelf clickedItem = (BookOrShelf) view.getTag();
        if (selectedItem == clickedItem)
            return true;

        if (selectedItem != null)
            clearSelection();

        selectedItem = clickedItem;
        notifyItemChanged(bookCollection.indexOf(selectedItem));
        return bookClickListener.onBookLongClick(selectedItem);
    }

    public BookOrShelf getSelectedItem(){
        return selectedItem;
    }

    // This also gets called in the course of changing the selection
    // We don't want to clear if the selection has already moved on
    public void unselect(BookOrShelf bookOrShelf) {
        if (bookOrShelf == selectedItem)
            clearSelection();
    }

    private void clearSelection(){
        BookOrShelf oldSelection = selectedItem;
        selectedItem = null;
        if (oldSelection != null)
            notifyItemChanged(bookCollection.indexOf(oldSelection));
    }

    public int highlightItem(BookOrShelf bookOrShelf){
        List<String> paths = new ArrayList<>(1);
        paths.add(bookOrShelf.pathOrUri);
        return highlightItems(paths);
    }

    public int highlightItems(List<String> paths){
        if (inHighlightedState)
            clearHighlight();

        int firstHighlighted = -1;
        inHighlightedState = true;
        for (int i=0; i<bookCollection.size(); ++i){
            BookOrShelf bookOrShelf = bookCollection.get(i);
            for(String path : paths){
                if (path.equals(bookOrShelf.pathOrUri)) {
                    bookOrShelf.highlighted = true;
                    if (firstHighlighted == -1)
                        firstHighlighted = i;
                }
            }
        }
        notifyDataSetChanged();
        return firstHighlighted;
    }

    private void clearHighlight(){
        if (!inHighlightedState)
            return;

        for (int i=0; i<bookCollection.size(); ++i)
            bookCollection.get(i).highlighted = false;

        inHighlightedState = false;

        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout linearLayout;
        public TextView bookNameView;
        public ImageView imageView;
        public ImageView speakerIcon;
        public BookOrShelf bookOrShelf;

        public ViewHolder(LinearLayout linearLayout) {
            super(linearLayout);
            this.linearLayout = linearLayout;
            bookNameView = (TextView) linearLayout.findViewById(R.id.title);
            imageView = (ImageView) linearLayout.findViewById(R.id.imageView);
            speakerIcon = (ImageView) linearLayout.findViewById(R.id.icon);
        }

        public Context getContext(){
            return linearLayout.getContext();
        }
    }

    public interface BookClickListener {
        void onBookClick(BookOrShelf bookOrShelf);
        boolean onBookLongClick(BookOrShelf bookOrShelf);
        void onClearBookSelection();
    }
}
