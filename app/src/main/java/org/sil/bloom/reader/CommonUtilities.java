package org.sil.bloom.reader;

import android.database.Cursor;

public class CommonUtilities {
    public static String getStringFromCursor(Cursor cursor, String column) {
        int iColumn = cursor.getColumnIndex(column);
        return iColumn >= 0 ? cursor.getString(iColumn) : null;
    }

    public static int getIntFromCursor(Cursor cursor, String column) {
        int iColumn = cursor.getColumnIndex(column);
        return iColumn >= 0 ? cursor.getInt(iColumn) : -1;
    }

    public static long getLongFromCursor(Cursor cursor, String column) {
        int iColumn = cursor.getColumnIndex(column);
        return iColumn >= 0 ? cursor.getLong(iColumn) : -1;
    }
}
