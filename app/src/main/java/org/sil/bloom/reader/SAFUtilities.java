package org.sil.bloom.reader;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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

    // Given a string that might be either a file path or the toString() of an SAF URI, return
    // the URI if that's what it is.
    public static Uri getContentUriIfItIsOne(String pathOrUri) {
        if (pathOrUri.startsWith("content://"))
            return Uri.parse(pathOrUri);
        return null;
    }

    public static boolean hasPermissionToBloomDirectory(Context context) {
        return hasPermission(context, BloomDirectoryTreeUri);
    }

    // If we already have a URI indicating we have permission to use the specified folder
    // (through SAF), return that URI. If not, return null.
    public static Uri getUriForFolderWithPermission(Context context, String folder) {
        // Typically, we are looking for something like /storage/1DE5-2C02/BloomExternal,
        // and if we have permission to access it, the URL will look like
        // content://com.android.externalstorage.documents/tree/1DE5-2C02%3ABloomExternal.
        // which has a getPath() of /tree/1DE5-2C02:BloomExternal
        // If we were looking for arbitrary folders, we'd have to give more thought to other
        // characters that may need encoding.
        String searchFor = "/" + folder.replace("/storage/", "").replace("/", ":");
        for(Uri uri:getUrisWithPermissions(context)) {
            if (uri.getPath().endsWith(searchFor)) {
                return uri;
            }
        }
        return null;
    }

    /**
     * FileProvider does not support converting the absolute path from
     * getExternalFilesDir() to a "content://" Uri. As "file://" Uri
     * has been blocked since Android 7+, we need to build the Uri
     * manually after discovering the external storage.
     * Adapted from https://github1s.com/syncthing/syncthing-android/blob/HEAD/app/src/main/java/com/nutomic/syncthingandroid/util/FileUtils.java#L140-L179,
     * Mozilla Public License 2.0.     */
    public static android.net.Uri getExternalFilesDirUri(Context context) {
        try {
            File remoteStorageDir = IOUtilities.removablePublicStorageRoot(context);
            if (remoteStorageDir == null) return null; // no removable storage
            File remoteBooksDir = new File(remoteStorageDir, "BloomExternal");
            if (!remoteBooksDir.exists()) return null; // No BloomExternal directory
            String absPath = remoteStorageDir.getAbsolutePath();
            String[] segments = absPath.split("/");
            if (segments.length < 3) {
                Log.w(TAG, "Could not extract volumeId from external storage path '" + absPath + "'");
                return null;
            }
            // Extract the volumeId, e.g. "abcd-efgh"
            String volumeId = segments[2];
            // This is a bit of magic adapted from the source above. I don't know how robust it will
            // prove across Android versions, or even across devices. There does not seem to be
            // any official way to do this, necessary though it seems to be if you are going to ask
            // the user for permissions on a particular folder.
            return Uri.parse(
                    "content://com.android.externalstorage.documents/document/" +
                            volumeId + "%3ABloomExternal");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // after catching exception.
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

    public static String fileNameFromUri(Uri uri) {
        String path = uri.getPath();
        int index = path.lastIndexOf(":");
        if (index < 0) {
            index = path.lastIndexOf("/");
            if (index == 0)
                return path; // unlikely to ever happen
        }
        return path.substring(index);
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

    public static int countBooksIn(Context context, Uri rootUri) {
        final int[] count = {0};
        BookSearchListener listener = new BookSearchListener() {
            @Override
            public void onNewBookOrShelf(File bloomdFile, Uri bookOrShelfUri) {
                count[0]++;
            }

            @Override
            public void onNewBloomBundle(Uri bundleUri) {

            }

            @Override
            public void onSearchComplete() {

            }
        };
        traverseDirectoryEntries(context, rootUri, listener);
        return count[0];
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

    public static Uri fileUriFromDirectoryUri(Context context, Uri dir, String name) {
        DocumentFile dfDir = DocumentFile.fromTreeUri(context, dir);
        DocumentFile dfFile = dfDir.findFile(name);
        if (dfFile == null)
            return null;
        return dfFile.getUri();
    }

    public static void copyUriToFile(Context context, Uri uri, File dest) {
        try {
            InputStream fs = context.getContentResolver().openInputStream(uri);
            OutputStream os = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int length;
            while ((length = fs.read(buf)) > 0) os.write(buf, 0, length);
            os.close();
            fs.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteUri(Context context, Uri uri) {
        try {
            DocumentsContract.deleteDocument(context.getContentResolver(),uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
