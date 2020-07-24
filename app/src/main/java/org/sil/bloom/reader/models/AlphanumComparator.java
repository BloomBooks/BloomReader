package org.sil.bloom.reader.models;

/*
 * The Alphanum Algorithm is an improved sorting algorithm for strings
 * containing numbers.  Instead of sorting numbers in ASCII order like
 * a standard sort, this algorithm sorts numbers in numeric order.
 *
 * The Alphanum Algorithm is discussed at http://www.DaveKoelle.com
 * The actual code is from http://www.davekoelle.com/alphanum.html.
 *
 * Released under the MIT License - https://opensource.org/licenses/MIT
 *
 * Copyright 2007-2017 David Koelle
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import java.util.Comparator;

/**
 * This is an updated version with enhancements made by Daniel Migowski,
 * Andre Bogus, and David Koelle. Updated by David Koelle in 2017.
 *
 * Some changes were made by the Bloom team for their needs in 2020.
 *
 * To use this class:
 *   Use the static "sort" method from the java.util.Collections class:
 *   Collections.sort(your list, new AlphanumComparator());
 */
public class AlphanumComparator implements Comparator<BookOrShelf> {
    private final boolean isDigit(char ch) {
        return ((ch >= 48) && (ch <= 57)); // Digits 0 through 9
    }

    /**
     * Length of string is passed in for improved efficiency (only need to calculate it once)
     **/
    private final String getChunk(String s, int slength, int marker) {
        StringBuilder chunk = new StringBuilder();
        char c = s.charAt(marker);
        chunk.append(c);
        marker++;
        if (isDigit(c)) {
            while (marker < slength) {
                c = s.charAt(marker);
                if (!isDigit(c))
                    break;
                chunk.append(c);
                marker++;
            }
        } else {
            while (marker < slength) {
                c = s.charAt(marker);
                if (isDigit(c))
                    break;
                chunk.append(c);
                marker++;
            }
        }
        return chunk.toString();
    }

    private final String trimLeadingZeroes(String chunk) {
        final String zero = "0";
        int len = chunk.length();
        int index = 0;
        char curCh;
        while (index < len) {
            curCh = chunk.charAt(index);
            if (curCh != 48) { // Found a non-zero!
                break;
            }
            index++;
        }
        return (index < len) ? chunk.substring(index): zero;
    }

    // This method and the one above (trimLeadingZeroes) are the main changes I (Gordon) have made to
    // the original alphanum comparator.
    // The changes I made bring the handling of nulls and case into line with what we had already
    // and fix a bug which that algorithm had with leading zeroes.
    public int compare(BookOrShelf one, BookOrShelf two) {
        // We don't expect all these null checks to be necessary,
        // but Play console shows we need at least some of them.

        if (one == null ^ two == null) {
            return (one == null) ? 1 : -1;
        }
        if (one == null && two == null) {
            return 0;
        }
        if (one.name == null ^ two.name == null) {
            return (one.name == null) ? 1 : -1;
        }
        String s1 = one.name;
        String s2 = two.name;
        if (s1 != null && s2 != null) {
            int nameCompare = compare(s1, s2);
            if (nameCompare != 0)
                return nameCompare;
        }
        String p1 = one.path;
        String p2 = two.path;
        if (p1 == null ^ p2 == null) {
            return (p1 == null) ? 1 : -1;
        }
        if (p1 == null && p2 == null) {
            return 0;
        }
        return compare(p1, p2);
    }

    // Slightly modified from the original to use trimLeadingZeroes() where needed.
    private int compare(String s1, String s2) {
        if ((s1 == null) || (s2 == null)) {
            return 0;
        }

        int thisMarker = 0;
        int thatMarker = 0;
        int s1Length = s1.length();
        int s2Length = s2.length();

        while (thisMarker < s1Length && thatMarker < s2Length) {
            String rawThisChunk = getChunk(s1, s1Length, thisMarker);
            thisMarker += rawThisChunk.length();
            String thisChunk = trimLeadingZeroes(rawThisChunk);

            String rawThatChunk = getChunk(s2, s2Length, thatMarker);
            thatMarker += rawThatChunk.length();
            String thatChunk = trimLeadingZeroes(rawThatChunk);

            // If both chunks contain numeric characters, sort them numerically
            int result = 0;
            if (isDigit(thisChunk.charAt(0)) && isDigit(thatChunk.charAt(0))) {
                // Simple chunk comparison by length.
                int thisChunkLength = thisChunk.length();
                result = thisChunkLength - thatChunk.length();
                // If equal, the first different number counts
                if (result == 0) {
                    for (int i = 0; i < thisChunkLength; i++) {
                        result = thisChunk.charAt(i) - thatChunk.charAt(i);
                        if (result != 0) {
                            return result;
                        }
                    }
                }
            } else {
                result = thisChunk.compareToIgnoreCase(thatChunk);
            }

            if (result != 0)
                return result;
        }

        return s1Length - s2Length;
    }
}
