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
import org.sil.bloom.reader.models.BookCollection;
import org.sil.bloom.reader.models.BookOrShelf;
import org.sil.bloom.reader.models.ExtStorageUnavailableException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.sil.bloom.reader.models.BookCollection.getLocalBooksDirectory;


public class IOUtilities {

    public static final String BLOOM_BUNDLE_FILE_EXTENSION = ".bloombundle";
    public static final String BLOOMD_FILE_EXTENSION = ".bloomd";

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
        try {
            new File(toPath).createNewFile(); //this does nothing if if already exists
            OutputStream out = new FileOutputStream(toPath);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = fromStream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            fromStream.close();
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
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

    // In case of doubt we return false
    public static boolean isInNonRemovableStorage(Context context, File file) {
        try {
            if (Build.VERSION.SDK_INT >= 21)
                return !Environment.isExternalStorageRemovable(file);

            if (Environment.isExternalStorageRemovable())
                return false; // Primary ext storage is removable. No confidence we can determine our file isn't on that

            // Since we're here, the primary external storage is nonremovable. Is our file in that directory?
            String extStoragePath = context.getExternalFilesDir("").getAbsolutePath();
            // Expect the path to be something like /path/to/nonremovable/ext/storage/Android/data/org.sil.bloom.reader/files/
            // Chopping off /Android/... gives us a path to nonremovable external storage
            int index = extStoragePath.indexOf(File.separator + "Android" + File.separator);
            if (index < 0)
                return false;  // Didn't get the path we expected
            extStoragePath = extStoragePath.substring(0, index);
            return file.getAbsolutePath().startsWith(extStoragePath);
        } catch (IllegalArgumentException e) {
            // This happens when trying to evaluate Environment.isExternalStorageRemovable() on
            // files from a content:// URI
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

    // not used. Considered zipping for .bloombundle, but the .bloomd files are already zipped,
    // so further compression is unlikely to be significant, and attempting it is likely to slow
    // things down. Hence the use of tar.
//    public static void zip(String directory, FilenameFilter filter, String destinationPath) throws IOException {
//        File[] fileList = new File(directory).listFiles(filter);
//        zip(fileList, destinationPath);
//    }
//
//    public static void zip(File[] files, String destinationPath) throws IOException {
//        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destinationPath)));
//        try {
//            byte data[] = new byte[BUFFER_SIZE];
//
//            for (int i = 0; i < files.length; i++) {
//                FileInputStream fi = new FileInputStream(files[i]);
//                BufferedInputStream origin = new BufferedInputStream(fi, BUFFER_SIZE);
//                try {
//                    String filePath = files[i].getAbsolutePath();
//                    ZipEntry entry = new ZipEntry(filePath.substring(filePath.lastIndexOf("/") + 1));
//                    out.putNextEntry(entry);
//                    int count;
//                    while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
//                        out.write(data, 0, count);
//                    }
//                }
//                finally {
//                    origin.close();
//                }
//            }
//        }
//        finally {
//            out.close();
//        }
//    }

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
                return filename.endsWith(BookOrShelf.BOOK_FILE_EXTENSION)
                        || filename.endsWith(BookOrShelf.BOOKSHELF_FILE_EXTENSION);
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
}
