package org.sil.bloom.reader;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import java.io.Closeable;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.sil.bloom.reader.IOUtilities.BLOOM_BUNDLE_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOKSHELF_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.BOOK_FILE_EXTENSION;
import static org.sil.bloom.reader.IOUtilities.ENCODED_FILE_EXTENSION;

public class SAFUtilities {
    private static final String TAG = "SAFUtilities";
    public static final  Uri BloomDirectoryUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ABloom");
    public static final  Uri BloomDirectoryTreeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ABloom");

    public static List<Uri> getUrisWithPermissions(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        return contentResolver.getPersistedUriPermissions().stream().map(UriPermission::getUri).collect(Collectors.toList());
    }

    public static boolean hasPermission(Context context, Uri uri) {
        return getUrisWithPermissions(context).contains(uri);
    }

    public static boolean hasPermissionToBloomDirectory(Context context) {
        return hasPermission(context, BloomDirectoryTreeUri);
    }

    public static Intent getDirectoryPermissionIntent(Uri initialDirectoryUri) {
        // Choose a directory using the system's file picker.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        // Provide read access to files and sub-directories in the user-selected directory.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when it loads.
        if (initialDirectoryUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialDirectoryUri);
        }

        return intent;
    }

    public static void searchDirectoryForBooks(Context context, Uri uri, BookSearchListener bookSearchListener) {
        //uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AAlarms");

        traverseDirectoryEntries(context, uri, bookSearchListener);


//                Uri childrenUri =
//                        DocumentsContract.buildChildDocumentsUriUsingTree(
//                                uri,
//                                DocumentsContract.getTreeDocumentId(uri)
//                        );
//                // get document file from children uri
//                DocumentFile tree = DocumentFile.fromTreeUri(this, childrenUri);
//                if (tree != null) {
//                    DocumentFile[] files = tree.listFiles();
//                    for (DocumentFile f : files) {
//                        Log.d("GetBooks", "f.getUri(): " + f.getUri() + "f.getName(): " + f.getName());
//                        //importBook(f.getUri(), f.getName(), false);
//                    }
//                }
        // get the list of the documents
        //tree?.listFiles()?.forEach { doc ->
        // get the input stream of a single document
        //val iss = contentResolver.openInputStream(doc.uri)
        // prepare the output stream
        //val oss = FileOutputStream(File(filesDir, doc.name))
        // copy the file
//                    CopyFile { result ->
//                            println("file copied? $result")
//                    }.execute(iss, oss)
//                }
    }

    private static void traverseDirectoryEntries(Context context, Uri rootUri, BookSearchListener bookSearchListener) {
        ContentResolver contentResolver = context.getContentResolver();
//        for (UriPermission perm:contentResolver.getPersistedUriPermissions()) {
//            Log.d(TAG, perm.getUri().toString());
//        }

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri));

        // Keep track of our directory hierarchy
        List<Uri> dirNodes = new LinkedList<>();
        dirNodes.add(childrenUri);

        while(!dirNodes.isEmpty()) {
            childrenUri = dirNodes.remove(0); // get the item from top
            Log.d(TAG, "node uri: " + childrenUri);
            Cursor c = contentResolver.query(childrenUri, new String[]{
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE},
                    null,
                    null,
                    null);
            try {
                while (c.moveToNext()) {
                    final String docId = c.getString(0);
                    final String name = c.getString(1);
                    final String mime = c.getString(2);
                    Log.d(TAG, "name: " + name + ", mime: " + mime);

                    if (isDirectory(mime)) {
                        final Uri newNode = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, docId);
                        dirNodes.add(newNode);
                    } else {
                        Uri uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId);
                        if (name.endsWith(BLOOM_BUNDLE_FILE_EXTENSION) ||
                                name.endsWith(BLOOM_BUNDLE_FILE_EXTENSION + ENCODED_FILE_EXTENSION)) {
                            bookSearchListener.onNewBloomBundle(uri);
                        } else if (name.endsWith(BOOK_FILE_EXTENSION) ||
                                name.endsWith(BOOK_FILE_EXTENSION + ENCODED_FILE_EXTENSION) ||
                                name.endsWith(BOOKSHELF_FILE_EXTENSION) ||
                                name.endsWith(BOOKSHELF_FILE_EXTENSION + ENCODED_FILE_EXTENSION)) {
                            bookSearchListener.onNewBookOrShelf(new File(uri.getPath()), uri);
                        }
                    }
                }
            } finally {
                closeQuietly(c);

                bookSearchListener.onSearchComplete();
            }
        }
    }

    // Util method to check if the mime type is a directory
    private static boolean isDirectory(String mimeType) {
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }

    // Util method to close a closeable
    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ignore) {
                // ignore exception
            }
        }
    }
}
