package org.sil.bloom.reader;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.sil.bloom.reader.models.BookCollection.getLocalBooksDirectory;


public class IOUtilities {
    public static final String BOOK_FILE_EXTENSION = ".bloomd";
    public static final String BOOKSHELF_FILE_EXTENSION = ".bloomshelf";
    public static final String BLOOM_BUNDLE_FILE_EXTENSION = ".bloombundle";

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
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs())
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                if (ze.isDirectory())
                    continue;
                FileOutputStream fout = new FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1)
                        fout.write(buffer, 0, count);
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
        FileDescriptor fd = context.getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor();
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(fd)));
        unzip(zis, targetDirectory);
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
            // Reading the file 1024 bytes at a time runs into trouble on some older
            // tablets.  So let's read it in bigger chunks.  For the problem book of
            // BL-6970, 4K chunks seemed to be enough, but let's be paranoid and use
            // an even bigger (but not outlandish) chunk size.  (If I could be sure
            // that target devices had at least 2GB, I'd opt for a 1MB buffer, but
            // since many older phones are only 512MB, a smaller buffer is better.)
            byte[] buffer = new byte[65536];  // 64K
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

    public static boolean copyFile(Context context, Uri bookUri, String toPath){
        try {
            FileDescriptor fd = context.getContentResolver().openFileDescriptor(bookUri, "r").getFileDescriptor();
            InputStream in = new FileInputStream(fd);
            return copyFile(in, toPath);
        } catch (IOException e){
            e.printStackTrace();
            return false;
        }
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
        TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(new FileOutputStream(destinationPath));
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
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(BOOK_FILE_EXTENSION)
                        || filename.endsWith(BOOKSHELF_FILE_EXTENSION);
            }
        };
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
        if (Build.VERSION.SDK_INT >= 21)
            return Environment.isExternalStorageRemovable(dir);

        boolean defaultStorageRemovable = Environment.isExternalStorageRemovable();
        if (dir.getPath().startsWith(Environment.getExternalStorageDirectory().getPath()))
            return defaultStorageRemovable;
        else
            return !defaultStorageRemovable;
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

    public static String getFilename(String path) {
        // Check for colon because files on SD card root have a path like
        // 1234-ABCD:book.bloomd
        int start = Math.max(path.lastIndexOf(File.separator),
                             path.lastIndexOf(':'))
                    + 1;
        return path.substring(start);
    }
}
