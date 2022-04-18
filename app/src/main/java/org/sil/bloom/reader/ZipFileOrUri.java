package org.sil.bloom.reader;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

// This class helps hide whether the source of a bloom book is a file or a URI.
// The book is conceptually decompressed into outputDir. If it comes from a file,
// things are only decompressed there as they are wanted. If it comes from a URI,
// we can only access the URI data sequentially, which makes it hopelessly slow
// to extract files on demand, so the best we can do is extract them all immediately.
public class ZipFileOrUri {

    ZipFile zipFile;
    String outputDir;

    public ZipFileOrUri(File input, String output) {
        try {
            zipFile = new ZipFile(input);
            outputDir = output;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public ZipFileOrUri(Uri uri, Context context, String output) {
        InputStream fs = null;
        try {
            outputDir = output;
            // All we can do is unzip the whole thing. I tried some code, which can be seen on the
            // SAFzipOneByOne branch, to extract individual files from the zip stream we can make
            // from the context and URI, but it is hopelessly slow; SAF seems to be forcing us to
            // read the whole file (at least as far as the thing we want) each time. Better, though
            // not good, to get them all in a single pass.
            IOUtilities.unzip(context, uri, new File(outputDir));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public File tryGetFile(String name) {
        if (zipFile != null) {
            try {
                final ZipEntry entry = zipFile.getEntry(name);
                if (entry == null) {
                    return null;
                }
                InputStream zin = zipFile.getInputStream(entry);
                return readFileFromInput(name, outputDir, zin);
            } catch (IOException e) {
                // fall through and return null.
            }
        } else {
                File result = new File(outputDir + File.separator + name);
                if (result.exists()) {
                    return result;
                }
                // fall through and return null;
        }
        return null;
    }

    private File readFileFromInput(String name, String outputDir, InputStream zin) throws IOException {
        File output = new File(outputDir + File.separator + name);
        output.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(output);
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zin.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.close();
        return output;
    }

    public void close() {
        if (zipFile != null) {
            try {
                zipFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Strictly looks for something at the top level with this extension.
    public File findFirstWithExtension(String extension) {
        return findFirstMatching(name -> name.indexOf("/") < 0 && name.endsWith(extension));
    }

    public File findFirstMatching(Predicate<String> condition) {
        try {
            Enumeration<? extends ZipEntry> entries;
            if (zipFile != null) {
                entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory())
                        continue;
                    if (condition.test(entry.getName())) {
                        InputStream zin = zipFile.getInputStream(entry);
                        return readFileFromInput("index.htm", outputDir, zin);
                    }
                }
            } else {
                return IOUtilities.findFirstMatching(new File(outputDir), condition);
            }
        } catch (IOException e) {
            // fall through and return null.
        }
        return null;
    }
}
