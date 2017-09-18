package org.sil.bloom.reader;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;


public class IOUtilities {


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
    public static void unzip(File zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
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

    // "Seem" because this doesn't actually have a way of knowing the physical location of things.
    // The modest goal here is to handle the common case that I'm aware of:
    // to differentiate between /storage/emulated/0 (internal) and /storage/53D-DS32 (external SD card).
    public static boolean seemToBeDifferentVolumes(String one, String two) {
        String[] oneParts = one.split("\\"+File.separator);
        String[] twoParts = two.split("\\"+File.separator);
        // for info on paths, https://www.reddit.com/r/Android/comments/496sn3/lets_clear_up_the_confusion_regarding_storage_in/?ref=share&ref_source=link
        // To would could perhaps only test the third part [2], for "emulated" vs. "53D-DS32". But testing all three doesn't hurt:
        if (oneParts.length < 3 || twoParts.length < 4)
            return false; // this is a surprising situation, play it safe
        return !oneParts[0].equals(twoParts[0]) || !oneParts[1].equals(twoParts[1]) || !oneParts[2].equals(twoParts[2]);
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
}
