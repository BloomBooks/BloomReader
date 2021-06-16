package org.sil.bloom.reader;

import org.junit.Test;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class IOUtilitiesTest {

    @Test
    public void getFilename_fileNameOnly_returnFileName() {
        String fileNameOrPath = "abc.bloomd";
        assertThat(IOUtilities.getFilename(fileNameOrPath), is("abc.bloomd"));
    }

    @Test
    public void getFilename_hasColon_returnFileName() {
        String fileNameOrPath = "something:abc.bloomd";
        assertThat(IOUtilities.getFilename(fileNameOrPath), is("abc.bloomd"));
    }

    @Test
    public void getFilename_hasSlash_returnFileName() {
        String fileNameOrPath = "something" + File.separator + "abc.bloomd";
        assertThat(IOUtilities.getFilename(fileNameOrPath), is("abc.bloomd"));
    }

    @Test
    public void getFilename_hasColonAndSlash_returnFileName() {
        String fileNameOrPath = "something:else" + File.separator + "abc.bloomd";
        assertThat(IOUtilities.getFilename(fileNameOrPath), is("abc.bloomd"));
    }

    @Test
    public void getFilename_hasMultipleSlashes_returnFileName() {
        String fileNameOrPath = "something" + File.separator + "else" + File.separator + "abc.bloomd";
        assertThat(IOUtilities.getFilename(fileNameOrPath), is("abc.bloomd"));
    }

    @Test
    public void getFilename_hasMultipleSlashesAndColon_returnFileName() {
        String fileNameOrPath = File.separator + "document" + File.separator + "primary:abc.bloomd";
        assertThat(IOUtilities.getFilename(fileNameOrPath), is("abc.bloomd"));
    }
}