package org.sil.bloom.reader;

// This class can be used to represent the content of one file, eg extracted from a zip archive.
public class TextFileContent {
    private String filename;
    private String encoding;

    public String Content;

    public TextFileContent(String filename)
    {
        this(filename, "UTF-8");
    }

    public TextFileContent(String filename, String encoding)
    {
        this.filename = filename;
        this.encoding = encoding;
    }

    public String getFilename() {
        return filename;
    }

    public String getEncoding() {
        return encoding;
    }
}
