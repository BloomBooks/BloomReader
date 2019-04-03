package org.sil.bloom.reader;

import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.BookOrShelf;
import org.sil.bloom.reader.BookListAdapter.ViewHolder;

/**
 * Used by the BookListAdapter to perform potentially slow operations associated with rendering
 * a BookOrShelf on a BookList on a background thread
 *
 * It sets the thumbnail image and toggles the speaker icon as appropriate
 */

public class SetBookListItemViewExtrasTask extends AsyncTask<Void, Void, SetBookListItemViewExtrasTask.TaskResult> {
    private ViewHolder viewHolder;
    private BookOrShelf bookOrShelf;

    SetBookListItemViewExtrasTask(@NonNull ViewHolder viewHolder){
        this.viewHolder = viewHolder;
        this.bookOrShelf = viewHolder.bookOrShelf;
    }

    // The only method that needs to be called
    // Sets the thumbnail and speaker icon first synchronously,
    // then asynchronously if needed (for books)
    public void setExtras() {
        setInitialImageView();
        clearSpeakerIcon();
        if (!bookOrShelf.isShelf()){
            // Async method only applies to books
            this.execute();
        }
    }

    // Synchronous method
    // Sets the proper thumbnail for shelves
    // and the fallback thumbnail for books
    private void setInitialImageView(){
        if (bookOrShelf.isShelf()) {
            viewHolder.imageView.setImageResource(R.drawable.bookshelf);
            try {
                viewHolder.imageView.setBackgroundColor(Color.parseColor("#" + bookOrShelf.backgroundColor));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        } else {
            viewHolder.imageView.setBackgroundColor(Color.argb(0, 0, 0, 0));
            viewHolder.imageView.setImageResource(R.drawable.book);
        }
    }

    // Synchronous method
    private void clearSpeakerIcon() {
        viewHolder.speakerIcon.setImageAlpha(0); // transparent
    }

    // Async method fetches the thumbnail and
    // determines if the book has audio
    // For books only
    @Override
    protected TaskResult doInBackground(Void... v) {
        Uri imageUri = BookCollection.getThumbnail(viewHolder.getContext(), bookOrShelf);
        boolean hasAudio = bookOrShelf.hasAudio();
        return new TaskResult(imageUri, hasAudio);
    }

    // Set the thumbnail and speaker icon
    // that were fetched in the background
    @Override
    protected void onPostExecute(TaskResult result) {
        // ViewHolder object can change books as the user scrolls
        // So we verify that this one still has the original book
        if (bookOrShelf == viewHolder.bookOrShelf) {
            if (result.imageUri != null)
                viewHolder.imageView.setImageURI(result.imageUri);
            int alpha = result.hasAudio ? 255 : 0;
            viewHolder.speakerIcon.setImageAlpha(alpha);
        }
    }

    public static class TaskResult{
        public Uri imageUri;
        public boolean hasAudio;

        TaskResult(Uri imageUri, boolean hasAudio) {
            this.imageUri = imageUri;
            this.hasAudio = hasAudio;
        }
    }
}

