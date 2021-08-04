package org.sil.bloom.reader;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import androidx.annotation.IntDef;

import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.sil.bloom.reader.models.BookOrShelf;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.Enumeration;

import static org.sil.bloom.reader.BloomReaderApplication.getBloomApplicationContext;
import static org.sil.bloom.reader.models.BookCollection.getLocalBooksDirectory;


public class IOUtilities {
    public static final String BOOK_FILE_EXTENSION = ".bloomd";
    public static final String BOOKSHELF_FILE_EXTENSION = ".bloomshelf";
    public static final String BLOOM_BUNDLE_FILE_EXTENSION = ".bloombundle";
    public static final String CHECKED_FILES_TAG = "org.sil.bloom.reader.checkedfiles";

    // Some file transfer mechanisms leave this appended to .bloomd (or .bloombundle)
    public static final String ENCODED_FILE_EXTENSION = ".enc";

    private static final int BUFFER_SIZE = 8192;

    public static void showError(Context context, CharSequence message) {
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    public static void emptyDirectory(File dir) {
        for (File child : dir.listFiles())
            deleteFileOrDirectory(child);
    }

    public static boolean isDirectoryEmpty(File dir) {
        return dir.listFiles().length == 0;
    }

    public static void deleteFileOrDirectory(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteFileOrDirectory(child);
        fileOrDirectory.delete();
    }

    //from http://stackoverflow.com/a/27050680
    public static void unzip(ZipInputStream zis, File targetDirectory) throws IOException {
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());

                // Prevent path traversal vulnerability. See https://support.google.com/faqs/answer/9294009.
                String fileCanonicalPath = file.getCanonicalPath();
                if (!fileCanonicalPath.startsWith(targetDirectory.getCanonicalPath())) {
                    throw new IOException(String.format("Zip file target path is invalid: %s", fileCanonicalPath));
                }

                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1) {
                        if (count == 0) {
                            // The zip header says we have more data for this entry, but we don't.
                            // The file must be truncated/corrupted.  See BL-6970.
                            throw new IOException("Invalid zip file");
                        }
                        fout.write(buffer, 0, count);
                    }
                } finally {
                    fout.close();
                }
            /* if time should be restored as well
            long time = ze.getTime();
            if (time > 0)
                file.setLastModified(time);
            */
            }
        } finally {
            zis.close();
        }
    }

    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        unzip(zis, targetDirectory);
    }

    public static void unzip(Context context, Uri uri, File targetDirectory) throws IOException {
        InputStream fs = context.getContentResolver().openInputStream(uri);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fs));
        unzip(zis, targetDirectory);
    }

    // Possible types of zip files to check.  (This list could be expanded if desired.)
    public static final int CHECK_ZIP = 0;
    public static final int CHECK_BLOOMD = 1;
    @IntDef({CHECK_ZIP, CHECK_BLOOMD})  // more efficient than enum types at run time.
    @Retention(RetentionPolicy.SOURCE)
    public @interface FileChecks {
    }

    private static SharedPreferences sCheckedFiles = null;

    // Check whether the given input file is a valid zip file.
    public static boolean isValidZipFile(File input) {
        return isValidZipFile(input, CHECK_ZIP);
    }


    public static boolean isValidZipFile(File input, @FileChecks int checkType) {
        return isValidZipFile(input, checkType, null);
    }

    // Check whether the given input file is a valid zip file that appears to have the proper data
    // for the given type.  We record the result of this check in a "SharedPreferences" file with
    // the modification time paired with the absolute pathname of the file.  If these match on the
    // next call, we'll return true without actually going through the slow process of unzipping
    // the whole file.  Note that this fast bypass ignores the checkType and desiredFile parameters.
    // The desiredFile parameter is designed to avoid having to unzip the file twice during startup,
    // once to ensure that it is valid and once to get the meta.json file content.
    public static boolean isValidZipFile(File input, @FileChecks int checkType, TextFileContent desiredFile) {
        String key = input.getAbsolutePath();
        if (sCheckedFiles == null) {
            Context context = getBloomApplicationContext();
            if (context != null) {
                sCheckedFiles = context.getSharedPreferences(CHECKED_FILES_TAG, 0);
            }
        }
        if (sCheckedFiles != null) {
            long timestamp = sCheckedFiles.getLong(key, 0L);
            if (timestamp == input.lastModified() && timestamp != 0L)
                return true;
        }
        try {
            // REVIEW very minimal check for .bloomd files: are there any filenames guaranteed to exist
            // in any .bloomd file regardless of age?
            int countHtml = 0;
            int countCss = 0;
            final ZipFile zipFile = new ZipFile(input);
            try {
                final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory())
                        continue;
                    String entryName = entry.getName().toLowerCase(Locale.ROOT);
                    // For validation purposes we're only interested in html files in the root directory.
                    // Activities, for example, may legitimately have their own.
                    if ((entryName.endsWith(".htm") || entryName.endsWith(".html")) && entryName.indexOf("/")< 0)
                        ++countHtml;
                    else if (entryName.endsWith(".css"))
                        ++countCss;
                    InputStream stream = zipFile.getInputStream(entry);
                    try {
                        int realSize = (int)entry.getSize();
                        byte[] buffer = new byte[realSize];
                        boolean inOnePass = true;   // valid data only if it's read by a single read operation.
                        int size = stream.read(buffer);
                        if (size != realSize && !(size == -1 && realSize == 0)) {
                            // The Java ZipEntry code does not always return the full data content even when the buffer is large
                            // enough for it.  Whether this is a bug or a feature, or just the way it is, depends on your point
                            // of view I suppose.  So we have a loop here since the initial read wasn't enough.
                            inOnePass = false;
                            int moreReadSize = stream.read(buffer);
                            do {
                                if (moreReadSize > 0) {
                                    size += moreReadSize;
                                    moreReadSize = stream.read(buffer);
                                }
                            } while (moreReadSize > 0);
                            if (size != realSize) {
                                // It would probably throw before getting here, but just in case, write
                                // out some debugging information and return false.
                                int compressedSize = (int)entry.getCompressedSize();
                                int method = entry.getMethod();
                                String type = "UNKNOWN (" + method + ")";
                                switch (entry.getMethod()) {
                                    case ZipEntry.STORED:
                                        type = "STORED";
                                        break;
                                    case ZipEntry.DEFLATED:
                                        type = "DEFLATED";
                                        break;
                                }
                                Log.e("IOUtilities", "Unzip size read " + size + " != size expected " + realSize +
                                        " for " + entry.getName() + " in " + input.getName() + ", compressed size = " + compressedSize + ", storage method = " + type);
                                return false;
                            }
                        }
                        if (inOnePass && desiredFile != null && entryName.equals(desiredFile.getFilename())) {
                            // save the desired file content so we won't have to unzip again
                            desiredFile.Content = new String(buffer, desiredFile.getEncoding());
                        }
                    } finally {
                        stream.close();
                    }
                }
            } finally {
                zipFile.close();
            }
            boolean retval;
            if (checkType == IOUtilities.CHECK_BLOOMD)
                retval = countHtml == 1 && countCss > 0;
            else
                retval = true;
            if (retval && sCheckedFiles != null) {
                SharedPreferences.Editor editor = sCheckedFiles.edit();
                editor.putLong(key, input.lastModified());
                editor.apply();
            }
            return retval;
        } catch (Exception e) {
            return false;
        }
    }

    // The same test, but here we only have available a URI.
    public static boolean isValidZipUri(Uri input, @FileChecks int checkType, TextFileContent desiredFile) {
        String key = input.toString();
        Context context = getBloomApplicationContext();
        if (sCheckedFiles == null) {
            if (context != null) {
                sCheckedFiles = context.getSharedPreferences(CHECKED_FILES_TAG, 0);
            }
        }
        if (sCheckedFiles != null) {
            long timestamp = sCheckedFiles.getLong(key, 0L);
            if (timestamp == lastModified(context, input) && timestamp != 0L)
                return true;
        }
        try {
            // REVIEW very minimal check for .bloomd files: are there any filenames guaranteed to exist
            // in any .bloomd file regardless of age?
            int countHtml = 0;
            int countCss = 0;
            InputStream fs = context.getContentResolver().openInputStream(input);
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fs));
            try {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory())
                        continue;
                    String entryName = entry.getName().toLowerCase(Locale.ROOT);
                    // For validation purposes we're only interested in html files in the root directory.
                    // Activities, for example, may legitimately have their own.
                    if ((entryName.endsWith(".htm") || entryName.endsWith(".html")) && entryName.indexOf("/") < 0)
                        ++countHtml;
                    else if (entryName.endsWith(".css"))
                        ++countCss;
                    int realSize = (int) entry.getSize();
                    byte[] buffer = new byte[realSize];
                    if (realSize != 0) {
                    // The Java ZipEntry code does not always return the full data content even when the buffer is large
                    // enough for it.  Whether this is a bug or a feature, or just the way it is, depends on your point
                    // of view I suppose.  So we have a loop here in case the initial read wasn't enough.
                    int size = 0;
                        int moreReadSize = zis.read(buffer, size, realSize - size);
                        while (moreReadSize > 0) {
                                size += moreReadSize;
                                moreReadSize = zis.read(buffer, size, realSize - size);
                        } ;
                        if (size != realSize) {
                            // It would probably throw before getting here, but just in case, write
                            // out some debugging information and return false.
                            int compressedSize = (int) entry.getCompressedSize();
                            int method = entry.getMethod();
                            String type = "UNKNOWN (" + method + ")";
                            switch (entry.getMethod()) {
                                case ZipEntry.STORED:
                                    type = "STORED";
                                    break;
                                case ZipEntry.DEFLATED:
                                    type Bloom= "DEFLATED";
                                    break;
                            }
                            Log.e("IOUtilities", "Unzip size read " + size + " != size expected " + realSize +
                                    " for " + entry.getName() + " in " + BookOrShelf.getNameFromPath(input.getPath()) + ", compressed size = " + compressedSize + ", storage method = " + type);
                            return false;
                        }
                    }
                    if (desiredFile != null && entryName.equals(desiredFile.getFilename())) {
                        // save the desired file content so we won't have to unzip again
                        desiredFile.Content = new String(buffer, desiredFile.getEncoding());
                    }
                }
            } finally {
                zis.close();
                fs.close();
            }
            boolean retval;
            if (checkType == IOUtilities.CHECK_BLOOMD)
                retval = countHtml == 1 && countCss > 0;
            else
                retval = true;
            if (retval && sCheckedFiles != null) {
                SharedPreferences.Editor editor = sCheckedFiles.edit();
                editor.putLong(key, lastModified(context, input));
                editor.apply();
            }
            return retval;
        } catch (Exception e) {
            return false;
        }
    }

    public static long lastModified(Context context, Uri uri) {
        long lastModified = 0;
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        try
        {
            if (cursor.moveToFirst())
                lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED));
        }
        finally
        {
            cursor.close();
        }

        return lastModified;
    }

    public static byte[] ExtractZipEntry(File input, String entryName) {
        try {
            ZipFile zip = new ZipFile(input);
            try {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    return null;
                }
                InputStream stream = zip.getInputStream(entry);
                try {
                    byte[] buffer = new byte[(int) entry.getSize()];
                    stream.read(buffer);
                    return buffer;
                } finally {
                    stream.close();
                }
            } finally {
                zip.close();
            }
        } catch (IOException e)
        {
            return null;
        }
    }

    public static boolean copyAssetFolder(AssetManager assetManager,
                                          String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            new File(toPath).mkdirs();
            boolean res = true;
            for (String file : files)
                if (file.contains("."))
                    res &= copyAsset(assetManager,
                            fromAssetPath + File.separator + file,
                            toPath + File.separator + file);
                else
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + File.separator + file,
                            toPath + File.separator + file);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toFilePath) {
        try {
            InputStream in = assetManager.open(fromAssetPath);
            return copyFile(in, toFilePath);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean copyFile(InputStream fromStream, String toPath) {
        int totalRead = 0;
        try {
            new File(toPath).createNewFile(); //this does nothing if if already exists
            OutputStream out = new FileOutputStream(toPath);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fromStream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalRead += read;
            }
            fromStream.close();
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            Log.e("IOUtilities", "Copied "+totalRead+" bytes to "+toPath+" before failing ("+e.getMessage()+")");
            e.printStackTrace();
            new File(toPath).delete();  // A partial file causes problems (BL-6970), so delete what we copied.
            return false;
        }
    }

    public static boolean copyFile(String fromPath, String toPath) {
        try {
            InputStream in = new FileInputStream(fromPath);
            return copyFile(in, toPath);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean copyBloomdFile(Context context, Uri bookUri, String toPath) {
        try {
            InputStream in = context.getContentResolver().openInputStream(bookUri);
            if (copyFile(in, toPath)) {
                // Even if the copy succeeds, if the result is not a valid .bloomd file, delete it
                // and fail.
                File newFile = new File(toPath);
                if (!isValidZipFile(newFile, CHECK_BLOOMD)) {
                    newFile.delete();
                    return false;
                }
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String FileToString(File file) {
        try {
            return InputStreamToString(new FileInputStream(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String InputStreamToString(InputStream inputStream) {
        try {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File findFirstWithExtension(File directory, final String extension){
        File[] paths = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String name) {
                return name.endsWith(extension);
            }
        });

        if (paths.length >= 1)
            return paths[0];
        return null;
    }

    public static void tar(String directory, FilenameFilter filter, String destinationPath) throws IOException {
        File[] fileList = new File(directory).listFiles(filter);
        tar(fileList, destinationPath);
    }

    public static void tar(File[] files, String destinationPath) throws IOException {
        File destination = new File(destinationPath);
        File destDirectory = destination.getParentFile();
        if (!destDirectory.exists())
            destDirectory.mkdirs();

        TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(new FileOutputStream(destinationPath));
        tarOutput.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
        for(File file : files) {
            TarArchiveEntry entry = new TarArchiveEntry(file, file.getName());
            tarOutput.putArchiveEntry(entry);
            FileInputStream in = new FileInputStream(file);
            IOUtils.copy(in, tarOutput);
            in.close();
            tarOutput.closeArchiveEntry();
        }
        tarOutput.close();
    }

    public static void makeBloomBundle(String destinationPath) throws IOException {

        FilenameFilter filter = (dir, filename) ->
                filename.endsWith(BOOK_FILE_EXTENSION) || filename.endsWith(BOOKSHELF_FILE_EXTENSION);
        tar(getLocalBooksDirectory().getAbsolutePath(), filter, destinationPath);
        //zip(getLocalBooksDirectory().getAbsolutePath(), filter, destinationPath);
    }

    public static String extractTarEntry(TarArchiveInputStream tarInput, String targetPath) throws IOException {
        ArchiveEntry entry = tarInput.getCurrentEntry();
        File destPath=new File(targetPath,entry.getName());
        if (!entry.isDirectory()) {
            FileOutputStream fout=new FileOutputStream(destPath);
            try{
                final byte[] buffer=new byte[8192];
                int n=0;
                while (-1 != (n=tarInput.read(buffer))) {
                    fout.write(buffer,0,n);
                }
                fout.close();
            }
            catch (IOException e) {
                fout.close();
                destPath.delete();
                tarInput.close();
                throw e;
            }
        }
        else {
            destPath.mkdir();
        }
        return destPath.getPath();
    }

    public static File nonRemovablePublicStorageRoot(Context context) {
        return publicStorageRoot(context, false);
    }

    public static File removablePublicStorageRoot(Context context) {
        return publicStorageRoot(context, true);
    }

    private static File publicStorageRoot(Context context, boolean removable) {
        if (Environment.isExternalStorageRemovable() == removable)
            return Environment.getExternalStorageDirectory();

        File[] appFilesDirs = context.getExternalFilesDirs(null);
        for (File appFilesDir : appFilesDirs) {
            if (appFilesDir != null) {
                File root  = storageRootFromAppFilesDir(appFilesDir);
                if (root != null && isRemovable(root) == removable)
                    return root;
            }
        }
        return null;
    }

    private static boolean isRemovable(File dir) {
        return Environment.isExternalStorageRemovable(dir);
    }

    private static File storageRootFromAppFilesDir(File appFilesDir) {
        // appStorageDir is a directory within the public storage with a path like
        // /path/to/public/storage/Android/data/org.sil.bloom.reader/files

        String path = appFilesDir.getPath();
        int androidDirIndex = path.indexOf(File.separator + "Android" + File.separator);
        if (androidDirIndex > 0)
            return new File(path.substring(0, androidDirIndex));
        return null;
    }

    static String getFilename(String path) {
        // Check for colon because files on SD card root have a path like
        // 1234-ABCD:book.bloomd
        int start = Math.max(path.lastIndexOf(File.separator),
                             path.lastIndexOf(':'))
                    + 1;
        return path.substring(start);
    }

    public static String getFileNameFromUri(Context context, Uri uri) {
        String fileName = null;
        try {
            String fileNameOrPath = IOUtilities.getFileNameOrPathFromUri(context, uri);
            if (fileNameOrPath != null && !fileNameOrPath.isEmpty())
                fileName = IOUtilities.getFilename(fileNameOrPath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return fileName;
    }

    public static String getFileNameOrPathFromUri(Context context, Uri uri) {
        String nameOrPath = uri.getPath();
        // Content URI's do not use the actual filename in the "path"
        if (uri.getScheme().equals("content")) {
            ContentResolver contentResolver = context.getContentResolver();
            if (contentResolver == null) // Play console showed us this could be null somehow
                return null;
            try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
                if (cursor != null) {
                    if (cursor.moveToFirst())
                        nameOrPath = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } catch (SecurityException se) {
                // Not sure how this happens, but we see it on the Play Console.
                // Perhaps someone has chosen Bloom Reader to try to process an intent we shouldn't be trying to handle?
                return null;
            }
        }
        return nameOrPath;
    }
}
