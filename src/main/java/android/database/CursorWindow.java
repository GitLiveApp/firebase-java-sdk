/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.database;

import android.annotation.BytesLong;
import android.annotation.IntRange;
import android.database.sqlite.SQLiteClosable;
import android.database.sqlite.SQLiteException;
import android.os.Parcel;
import com.google.common.base.Preconditions;
import org.sqlite.core.Codes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A buffer containing multiple cursor rows.
 * <p>
 * A {@link CursorWindow} is read-write when initially created and used locally.
 * When sent to a remote process (by writing it to a {@link Parcel}), the remote process
 * receives a read-only view of the cursor window.  Typically the cursor window
 * will be allocated by the producer, filled with data, and then sent to the
 * consumer for reading.
 * </p>
 */
public class CursorWindow extends SQLiteClosable {

    private final List<Row> rows;
    private int numColumns;

    private static final String STATS_TAG = "CursorWindowStats";

    // This static member will be evaluated when first used.
    private static int sCursorWindowSize = -1;

    private int mStartPos;
    private final String mName;

    /**
     * Creates a new empty cursor window and gives it a name.
     * <p>
     * The cursor initially has no rows or columns.  Call {@link #setNumColumns(int)} to
     * set the number of columns before adding any rows to the cursor.
     * </p>
     *
     * @param name The name of the cursor window, or null if none.
     */
    public CursorWindow(String name) {
        this(name, getCursorWindowSize());
    }

    /**
     * Creates a new empty cursor window and gives it a name.
     * <p>
     * The cursor initially has no rows or columns.  Call {@link #setNumColumns(int)} to
     * set the number of columns before adding any rows to the cursor.
     * </p>
     *
     * @param name The name of the cursor window, or null if none.
     * @param windowSizeBytes Size of cursor window in bytes.
     * @throws IllegalArgumentException if {@code windowSizeBytes} is less than 0
     * @throws AssertionError if created window pointer is 0
     * <p><strong>Note:</strong> Memory is dynamically allocated as data rows are added to the
     * window. Depending on the amount of data stored, the actual amount of memory allocated can be
     * lower than specified size, but cannot exceed it.
     */
    public CursorWindow(String name, @BytesLong long windowSizeBytes) {
        if (windowSizeBytes < 0) {
            throw new IllegalArgumentException("Window size cannot be less than 0");
        }
        mStartPos = 0;
        mName = name != null && !name.isEmpty() ? name : "<unnamed>";
        this.rows = new ArrayList<Row>();
    }

    /**
     * Creates a new empty cursor window.
     * <p>
     * The cursor initially has no rows or columns.  Call {@link #setNumColumns(int)} to
     * set the number of columns before adding any rows to the cursor.
     * </p>
     *
     * @param localWindow True if this window will be used in this process only,
     * false if it might be sent to another processes.  This argument is ignored.
     *
     * @deprecated There is no longer a distinction between local and remote
     * cursor windows.  Use the {@link #CursorWindow(String)} constructor instead.
     */
    @Deprecated
    public CursorWindow(boolean localWindow) {
        this((String)null);
    }

//    private CursorWindow(Parcel source) {
//        mStartPos = source.readInt();
//        mWindowPtr = nativeCreateFromParcel(source);
//        if (mWindowPtr == 0) {
//            throw new AssertionError(); // Not possible, the native code won't return it.
//        }
//        mName = nativeGetName(mWindowPtr);
//        mCloseGuard.open("CursorWindow.close");
//    }

    @Override
    protected void finalize() throws Throwable {
        try {
            dispose();
        } finally {
            super.finalize();
        }
    }

    private void dispose() {
        rows.clear();
    }

    /**
     * Gets the name of this cursor window, never null.
     * @hide
     */
    public String getName() {
        return mName;
    }

    /**
     * Clears out the existing contents of the window, making it safe to reuse
     * for new data.
     * <p>
     * The start position ({@link #getStartPosition()}), number of rows ({@link #getNumRows()}),
     * and number of columns in the cursor are all reset to zero.
     * </p>
     */
    public void clear() {
        rows.clear();
    }

    /**
     * Gets the start position of this cursor window.
     * <p>
     * The start position is the zero-based index of the first row that this window contains
     * relative to the entire result set of the {@link Cursor}.
     * </p>
     *
     * @return The zero-based start position.
     */
    public @IntRange(from = 0) int getStartPosition() {
        return mStartPos;
    }

    /**
     * Sets the start position of this cursor window.
     * <p>
     * The start position is the zero-based index of the first row that this window contains
     * relative to the entire result set of the {@link Cursor}.
     * </p>
     *
     * @param pos The new zero-based start position.
     */
    public void setStartPosition(@IntRange(from = 0) int pos) {
        mStartPos = pos;
    }

    /**
     * Gets the number of rows in this window.
     *
     * @return The number of rows in this cursor window.
     */
    public @IntRange(from = 0) int getNumRows() {
        return rows.size();
    }

    /**
     * Sets the number of columns in this window.
     * <p>
     * This method must be called before any rows are added to the window, otherwise
     * it will fail to set the number of columns if it differs from the current number
     * of columns.
     * </p>
     *
     * @param columnNum The new number of columns.
     * @return True if successful.
     */
    public boolean setNumColumns(@IntRange(from = 0) int columnNum) {
        this.numColumns = columnNum;
        return true;
    }

    /**
     * Allocates a new row at the end of this cursor window.
     *
     * @return True if successful, false if the cursor window is out of memory.
     */

    public boolean allocRow() {
        rows.add(new Row(numColumns));
        return true;
    }

    /**
     * Frees the last row in this cursor window.
     */
    public void freeLastRow(){
        rows.remove(rows.size()-1);
    }

    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_NULL}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_NULL}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isNull(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return getType(row, column) == Cursor.FIELD_TYPE_NULL;
    }

    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_BLOB} or {@link Cursor#FIELD_TYPE_NULL}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_BLOB} or
     * {@link Cursor#FIELD_TYPE_NULL}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isBlob(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        int type = getType(row, column);
        return type == Cursor.FIELD_TYPE_BLOB || type == Cursor.FIELD_TYPE_NULL;
    }

    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_INTEGER}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_INTEGER}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isLong(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return getType(row, column) == Cursor.FIELD_TYPE_INTEGER;
    }

    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_FLOAT}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_FLOAT}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isFloat(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return getType(row, column) == Cursor.FIELD_TYPE_FLOAT;
    }

    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_STRING} or {@link Cursor#FIELD_TYPE_NULL}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_STRING}
     * or {@link Cursor#FIELD_TYPE_NULL}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isString(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        int type = getType(row, column);
        return type == Cursor.FIELD_TYPE_STRING || type == Cursor.FIELD_TYPE_NULL;
    }

    private Value value(int rowN, int colN) {
        Row row = rows.get(rowN);
        if (row == null) {
            throw new IllegalArgumentException("Bad row number: " + rowN + ", count: " + rows.size());
        }
        return row.get(colN);
    }

    private boolean putValue(Value value, int rowN, int colN) {
        return rows.get(rowN).set(colN, value);
    }

    /**
     * Returns the type of the field at the specified row and column index.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The field type.
     */
    public @Cursor.FieldType int getType(@IntRange(from = 0) int row,
                                         @IntRange(from = 0) int column) {
        return value(row - mStartPos, column).type;
    }

    /**
     * Gets the value of the field at the specified row and column index as a byte array.
     * <p>
     * The result is determined as follows:
     * <ul>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the result
     * is <code>null</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then the result
     * is the blob value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the result
     * is the array of bytes that make up the internal representation of the
     * string value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER} or
     * {@link Cursor#FIELD_TYPE_FLOAT}, then a {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a byte array.
     */
    public byte[] getBlob(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        Value value = value(row - mStartPos, column);

        switch (value.type) {
            case Cursor.FIELD_TYPE_NULL:
                return null;
            case Cursor.FIELD_TYPE_BLOB:
                // This matches Android's behavior, which does not match the SQLite spec
                byte[] blob = (byte[])value.value;
                return blob == null ? new byte[]{} : blob;
            case Cursor.FIELD_TYPE_STRING:
                // Matches the Android behavior to contain a zero-byte at the end
                byte[] stringBytes = ((String) value.value).getBytes(UTF_8);
                return Arrays.copyOf(stringBytes, stringBytes.length + 1);
            default:
                throw new android.database.sqlite.SQLiteException(
                        "Getting blob when column is non-blob. Row " + row + ", col " + column);
        }    }

    /**
     * Gets the value of the field at the specified row and column index as a string.
     * <p>
     * The result is determined as follows:
     * <ul>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the result
     * is <code>null</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the result
     * is the string value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER}, then the result
     * is a string representation of the integer in decimal, obtained by formatting the
     * value with the <code>printf</code> family of functions using
     * format specifier <code>%lld</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_FLOAT}, then the result
     * is a string representation of the floating-point value in decimal, obtained by
     * formatting the value with the <code>printf</code> family of functions using
     * format specifier <code>%g</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then a
     * {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a string.
     */
    public String getString(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        Value val = value(row - mStartPos, column);
        if (val.type == Cursor.FIELD_TYPE_BLOB) {
            throw new android.database.sqlite.SQLiteException(
                    "Getting string when column is blob. Row " + row + ", col " + column);
        }
        Object value = val.value;
        return value == null ? null : String.valueOf(value);
    }

    /**
     * Copies the text of the field at the specified row and column index into
     * a {@link CharArrayBuffer}.
     * <p>
     * The buffer is populated as follows:
     * <ul>
     * <li>If the buffer is too small for the value to be copied, then it is
     * automatically resized.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the buffer
     * is set to an empty string.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the buffer
     * is set to the contents of the string.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER}, then the buffer
     * is set to a string representation of the integer in decimal, obtained by formatting the
     * value with the <code>printf</code> family of functions using
     * format specifier <code>%lld</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_FLOAT}, then the buffer is
     * set to a string representation of the floating-point value in decimal, obtained by
     * formatting the value with the <code>printf</code> family of functions using
     * format specifier <code>%g</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then a
     * {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @param buffer The {@link CharArrayBuffer} to hold the string.  It is automatically
     * resized if the requested string is larger than the buffer's current capacity.
     */
//    public void copyStringToBuffer(@IntRange(from = 0) int row, @IntRange(from = 0) int column,
//                                   CharArrayBuffer buffer) {
//        if (buffer == null) {
//            throw new IllegalArgumentException("CharArrayBuffer should not be null");
//        }
//        acquireReference();
//        try {
//            nativeCopyStringToBuffer(mWindowPtr, row - mStartPos, column, buffer);
//        } finally {
//            releaseReference();
//        }
//    }

    private Number getNumber(int row, int column) {
        Value value = value(row, column);
        switch (value.type) {
            case Cursor.FIELD_TYPE_NULL:
            case Codes.SQLITE_NULL:
                return 0;
            case Cursor.FIELD_TYPE_INTEGER:
            case Cursor.FIELD_TYPE_FLOAT:
                return (Number) value.value;
            case Cursor.FIELD_TYPE_STRING: {
                try {
                    return Double.parseDouble((String) value.value);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            case Cursor.FIELD_TYPE_BLOB:
                throw new android.database.sqlite.SQLiteException("could not convert "+value);
            default:
                throw new android.database.sqlite.SQLiteException("unknown type: "+value.type);
        }
    }

    /**
     * Gets the value of the field at the specified row and column index as a <code>long</code>.
     * <p>
     * The result is determined as follows:
     * <ul>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the result
     * is <code>0L</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the result
     * is the value obtained by parsing the string value with <code>strtoll</code>.
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER}, then the result
     * is the <code>long</code> value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_FLOAT}, then the result
     * is the floating-point value converted to a <code>long</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then a
     * {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a <code>long</code>.
     */
    public long getLong(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return getNumber(row - mStartPos, column).longValue();
    }

    /**
     * Gets the value of the field at the specified row and column index as a
     * <code>double</code>.
     * <p>
     * The result is determined as follows:
     * <ul>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the result
     * is <code>0.0</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the result
     * is the value obtained by parsing the string value with <code>strtod</code>.
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER}, then the result
     * is the integer value converted to a <code>double</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_FLOAT}, then the result
     * is the <code>double</code> value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then a
     * {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a <code>double</code>.
     */
    public double getDouble(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return getNumber(row - mStartPos, column).doubleValue();
    }

    /**
     * Gets the value of the field at the specified row and column index as a
     * <code>short</code>.
     * <p>
     * The result is determined by invoking {@link #getLong} and converting the
     * result to <code>short</code>.
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a <code>short</code>.
     */
    public short getShort(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return (short) getLong(row, column);
    }

    /**
     * Gets the value of the field at the specified row and column index as an
     * <code>int</code>.
     * <p>
     * The result is determined by invoking {@link #getLong} and converting the
     * result to <code>int</code>.
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as an <code>int</code>.
     */
    public int getInt(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return (int) getLong(row, column);
    }

    /**
     * Gets the value of the field at the specified row and column index as a
     * <code>float</code>.
     * <p>
     * The result is determined by invoking {@link #getDouble} and converting the
     * result to <code>float</code>.
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as an <code>float</code>.
     */
    public float getFloat(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return (float) getDouble(row, column);
    }

    /**
     * Copies a byte array into the field at the specified row and column index.
     *
     * @param value The value to store.
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putBlob(byte[] value,
                           @IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        // Real Android will crash in native code if putString is called with a null value.
        Preconditions.checkNotNull(value);
        return putValue(new Value(value, Cursor.FIELD_TYPE_BLOB), row, column);
    }

    /**
     * Copies a string into the field at the specified row and column index.
     *
     * @param value The value to store.
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putString(String value,
                             @IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        // Real Android will crash in native code if putString is called with a null value.
        Preconditions.checkNotNull(value);
        return putValue(new Value(value, Cursor.FIELD_TYPE_STRING), row, column);
    }

    /**
     * Puts a long integer into the field at the specified row and column index.
     *
     * @param value The value to store.
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putLong(long value,
                           @IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return putValue(new Value(value, Cursor.FIELD_TYPE_INTEGER), row, column);
    }

    /**
     * Puts a double-precision floating point value into the field at the
     * specified row and column index.
     *
     * @param value The value to store.
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putDouble(double value,
                             @IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return putValue(new Value(value, Cursor.FIELD_TYPE_FLOAT), row, column);
    }

    /**
     * Puts a null value into the field at the specified row and column index.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putNull(@IntRange(from = 0) int row, @IntRange(from = 0) int column) {
        return putValue(new Value(null, Cursor.FIELD_TYPE_NULL), row, column);
    }

//    public static final @android.annotation.NonNull Parcelable.Creator<CursorWindow> CREATOR
//            = new Parcelable.Creator<CursorWindow>() {
//        public CursorWindow createFromParcel(Parcel source) {
//            return new CursorWindow(source);
//        }
//
//        public CursorWindow[] newArray(int size) {
//            return new CursorWindow[size];
//        }
//    };

//    public static CursorWindow newFromParcel(Parcel p) {
//        return CREATOR.createFromParcel(p);
//    }

    public int describeContents() {
        return 0;
    }

//    public void writeToParcel(Parcel dest, int flags) {
//        acquireReference();
//        try {
//            dest.writeInt(mStartPos);
//            nativeWriteToParcel(mWindowPtr, dest);
//        } finally {
//            releaseReference();
//        }
//
//        if ((flags & Parcelable.PARCELABLE_WRITE_RETURN_VALUE) != 0) {
//            releaseReference();
//        }
//    }

    @Override
    protected void onAllReferencesReleased() {
        dispose();
    }

    private static int getCursorWindowSize() {
        if (sCursorWindowSize < 0) {
            // The cursor window size. resource xml file specifies the value in kB.
            // convert it to bytes here by multiplying with 1024.
            sCursorWindowSize = 2024 * 1024;
        }
        return sCursorWindowSize;
    }

    @Override
    public String toString() {
        return getName();
    }

    private static class Row {
        private final List<Value> values;

        public Row(int length) {
            values = new ArrayList<Value>(length);
            for (int i=0; i<length; i++) {
                values.add(new Value(null, Cursor.FIELD_TYPE_NULL));
            }
        }

        public Value get(int n) {
            return values.get(n);
        }

        public boolean set(int colN, Value value) {
            values.set(colN, value);
            return true;
        }
    }

    private static class Value {
        private final Object value;
        private final int type;

        public Value(final Object value, final int type) {
            this.value = value;
            this.type = type;
        }
    }
}