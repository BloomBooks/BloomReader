package org.sil.bloom.reader;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
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
        BookOrShelf bookOrShelf = bookCollection.get(position);
        holder.linearLayout.setTag(bookOrShelf);
        holder.bookNameView.setText(bookOrShelf.name);
        setImageView(bookOrShelf, holder);
        setBackgroundColor(bookOrShelf, holder);
    }

    private void setImageView(BookOrShelf bookOrShelf, ViewHolder holder){
        if (bookOrShelf.isShelf()) {
            holder.imageView.setImageResource(R.drawable.bookshelf);
            try {
                holder.imageView.setBackgroundColor(Color.parseColor("#" + bookOrShelf.backgroundColor));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        } else {
            holder.imageView.setBackgroundColor(Color.argb(0, 0, 0, 0));
            Uri image_uri = bookCollection.getThumbnail(holder.getContext(), bookOrShelf);
            if(image_uri != null)
                holder.imageView.setImageURI(image_uri);
            else
                holder.imageView.setImageResource(R.drawable.book);
        }
    }

    private void setBackgroundColor(BookOrShelf bookOrShelf, ViewHolder holder){
        if (bookOrShelf == selectedItem)
            holder.linearLayout.setBackgroundColor(ContextCompat.getColor(holder.getContext(), R.color.colorAccent));
        else if (bookOrShelf.highlighted)
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
        clearHighlight();
        if (selectedItem != null) {
            clearSelection();
            bookClickListener.onClearBookSelection();
        }
        else {
            bookClickListener.onBookClick((BookOrShelf) view.getTag());
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (selectedItem == null) {
            selectedItem = (BookOrShelf) view.getTag();
            notifyItemChanged(bookCollection.indexOf(selectedItem));
            return bookClickListener.onBookLongClick();
        }
        else {
            clearSelection();
            selectedItem = (BookOrShelf) view.getTag();
            notifyItemChanged(bookCollection.indexOf(selectedItem));
            return true;
        }
    }

    public BookOrShelf getSelectedItem(){
        return selectedItem;
    }

    public void clearSelection(){
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

        // With the current usages of this method, notifyDataSetChanged() is unecessary
        // and causes an undesirable delay in processing onClick().
        // notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout linearLayout;
        public TextView bookNameView;
        public ImageView imageView;

        public ViewHolder(LinearLayout linearLayout) {
            super(linearLayout);
            this.linearLayout = linearLayout;
            bookNameView = (TextView) linearLayout.findViewById(R.id.title);
            imageView = (ImageView) linearLayout.findViewById(R.id.imageView);
        }

        public Context getContext(){
            return linearLayout.getContext();
        }
    }

    public interface BookClickListener {
        void onBookClick(BookOrShelf bookOrShelf);
        boolean onBookLongClick();
        void onClearBookSelection();
    }
}
