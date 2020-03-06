package org.sil.bloom.reader;

import android.content.Context;
import android.graphics.Color;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
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
        holder.bookNameView.setText(holder.bookOrShelf.name);
        new SetBookListItemViewExtrasTask(holder).setExtras(); // Sets the thumbnail and speaker icon
        setBackgroundColor(holder);
    }


    private void setBackgroundColor(ViewHolder holder){
        if (holder.bookOrShelf == selectedItem)
            holder.linearLayout.setBackgroundColor(ContextCompat.getColor(holder.getContext(), R.color.colorAccent));
        else if (holder.bookOrShelf.highlighted)
            holder.linearLayout.setBackgroundColor(ContextCompat.getColor(holder.getContext(), R.color.new_book_highlight));
        else
            holder.linearLayout.setBackgroundColor(Color.WHITE);
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
        paths.add(bookOrShelf.path);
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
                if (path.equals(bookOrShelf.path)) {
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
