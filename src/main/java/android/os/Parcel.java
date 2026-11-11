package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.ArrayMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A container for the values a {@link Parcelable} flattens itself into, with the Android class's API for the members
 * the ported Firebase libraries and their consumers use. The repackaged Firebase classes (Timestamp, RemoteMessage,
 * Trace and others) expose {@code writeToParcel(Parcel, int)} and a {@code CREATOR}, so this class exists mainly so
 * that code compiled against them can reference it.
 * <p>
 * Nothing is transported between processes on the JVM, so there is no binary format to be compatible with: the parcel
 * is an in-memory list of the values written to it, read back in the same order. {@link #dataPosition()},
 * {@link #setDataPosition} and {@link #dataSize()} therefore count values, not bytes, and {@link #marshall()} is a
 * Java-serialized copy of those values that only {@link #unmarshall} understands. Reading past the end or reading a
 * value as another type throws an {@link IllegalStateException}. Instances are not pooled: {@link #obtain()} creates a
 * fresh parcel and {@link #recycle()} empties it.
 */
public final class Parcel {

    private static final int VAL_NULL = -1;
    private static final int VAL_STRING = 0;
    private static final int VAL_INTEGER = 1;
    private static final int VAL_MAP = 2;
    private static final int VAL_BUNDLE = 3;
    private static final int VAL_PARCELABLE = 4;
    private static final int VAL_LONG = 6;
    private static final int VAL_FLOAT = 7;
    private static final int VAL_DOUBLE = 8;
    private static final int VAL_BOOLEAN = 9;
    private static final int VAL_LIST = 11;
    private static final int VAL_BYTEARRAY = 13;
    private static final int VAL_STRINGARRAY = 14;
    private static final int VAL_BYTE = 20;
    private static final int VAL_SERIALIZABLE = 21;

    private static final int EX_SECURITY = -1;
    private static final int EX_ILLEGAL_ARGUMENT = -3;
    private static final int EX_NULL_POINTER = -4;
    private static final int EX_ILLEGAL_STATE = -5;
    private static final int EX_UNSUPPORTED_OPERATION = -7;

    private final List<Object> values = new ArrayList<>();
    private int position;

    private Parcel() {
    }

    /** A new, empty parcel. */
    @NonNull
    public static Parcel obtain() {
        return new Parcel();
    }

    /** Empties the parcel so that it can be written again. */
    public void recycle() {
        values.clear();
        position = 0;
    }

    /** The number of values written to the parcel. */
    public int dataSize() {
        return values.size();
    }

    /** The number of values left to read from the current position. */
    public int dataAvail() {
        return values.size() - position;
    }

    /** The index of the next value to read or write. */
    public int dataPosition() {
        return position;
    }

    /** Moves to the value at {@code pos}, which is between 0 and {@link #dataSize()} inclusive. */
    public void setDataPosition(int pos) {
        if (pos < 0 || pos > values.size()) {
            throw new IllegalArgumentException("Position " + pos + " is outside the parcel of size " + values.size());
        }
        position = pos;
    }

    /** The values of the parcel as bytes that {@link #unmarshall} can read back into a parcel. */
    @NonNull
    public byte[] marshall() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new ArrayList<>(values));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to marshall the parcel", e);
        }
        return bytes.toByteArray();
    }

    /**
     * Replaces the contents of the parcel with the values {@link #marshall()} produced, leaving the position at the end
     * like Android does: call {@link #setDataPosition setDataPosition(0)} before reading them.
     */
    @SuppressWarnings("unchecked")
    public void unmarshall(@NonNull byte[] data, int offset, int length) {
        List<Object> unmarshalled;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data, offset, length))) {
            unmarshalled = (List<Object>) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new IllegalArgumentException("The data is not a marshalled parcel", e);
        }
        values.clear();
        values.addAll(unmarshalled);
        position = values.size();
    }

    public void writeByte(byte val) {
        writeInt(val);
    }

    public void writeInt(int val) {
        write(val);
    }

    public void writeLong(long val) {
        write(val);
    }

    public void writeFloat(float val) {
        write(val);
    }

    public void writeDouble(double val) {
        write(val);
    }

    public void writeBoolean(boolean val) {
        writeInt(val ? 1 : 0);
    }

    public void writeString(@Nullable String val) {
        write(val);
    }

    /** The same as {@link #writeString}: there is no UTF-8 encoding to choose when nothing is encoded. */
    public void writeString8(@Nullable String val) {
        write(val);
    }

    /** Writes the array as a single value, or a null value for a null array. */
    public void writeByteArray(@Nullable byte[] b) {
        write(b == null ? null : b.clone());
    }

    public void writeByteArray(@Nullable byte[] b, int offset, int len) {
        if (b == null) {
            write(null);
            return;
        }
        byte[] copy = new byte[len];
        System.arraycopy(b, offset, copy, 0, len);
        write(copy);
    }

    /** Writes the size of the list, or -1 for a null list, followed by its strings. */
    public void writeStringList(@Nullable List<String> val) {
        if (val == null) {
            writeInt(-1);
            return;
        }
        writeInt(val.size());
        for (String s : val) {
            writeString(s);
        }
    }

    /** Writes the size of the list, or -1 for a null list, followed by its elements with {@link #writeValue}. */
    public void writeList(@Nullable List val) {
        if (val == null) {
            writeInt(-1);
            return;
        }
        writeInt(val.size());
        for (Object o : val) {
            writeValue(o);
        }
    }

    /** Writes the size of the map, or -1 for a null map, followed by each key and value with {@link #writeValue}. */
    public void writeMap(@Nullable Map val) {
        if (val == null) {
            writeInt(-1);
            return;
        }
        writeInt(val.size());
        for (Object entry : val.entrySet()) {
            writeValue(((Map.Entry<?, ?>) entry).getKey());
            writeValue(((Map.Entry<?, ?>) entry).getValue());
        }
    }

    /** Writes the size of the map, or -1 for a null map, followed by each key as a string and each value with {@link #writeValue}. */
    public void writeArrayMap(@Nullable ArrayMap<String, Object> val) {
        if (val == null) {
            writeInt(-1);
            return;
        }
        writeInt(val.size());
        for (int i = 0; i < val.size(); i++) {
            writeString(val.keyAt(i));
            writeValue(val.valueAt(i));
        }
    }

    /** Writes the size of the bundle, or -1 for a null bundle, followed by each key as a string and each value with {@link #writeValue}. */
    public void writeBundle(@Nullable Bundle val) {
        if (val == null) {
            writeInt(-1);
            return;
        }
        writeInt(val.size());
        for (String key : val.keySet()) {
            writeString(key);
            writeValue(val.get(key));
        }
    }

    /**
     * Writes the name of the Parcelable's class, or a null string for a null Parcelable, and then lets it write itself
     * with {@link Parcelable#writeToParcel}.
     */
    public void writeParcelable(@Nullable Parcelable p, int parcelableFlags) {
        if (p == null) {
            writeString(null);
            return;
        }
        writeString(p.getClass().getName());
        p.writeToParcel(this, parcelableFlags);
    }

    /** Writes the object itself, which must be Serializable for {@link #marshall()} to work. */
    public void writeSerializable(@Nullable Serializable s) {
        write(s);
    }

    /**
     * Writes a value of one of the types the typed write methods take, with a tag for {@link #readValue} to know which,
     * or any other Serializable value as is.
     *
     * @throws IllegalArgumentException for a value of any other type
     */
    public void writeValue(@Nullable Object v) {
        if (v == null) {
            writeInt(VAL_NULL);
        } else if (v instanceof String) {
            writeInt(VAL_STRING);
            writeString((String) v);
        } else if (v instanceof Integer) {
            writeInt(VAL_INTEGER);
            writeInt((Integer) v);
        } else if (v instanceof Map) {
            writeInt(VAL_MAP);
            writeMap((Map) v);
        } else if (v instanceof Bundle) {
            writeInt(VAL_BUNDLE);
            writeBundle((Bundle) v);
        } else if (v instanceof Parcelable) {
            writeInt(VAL_PARCELABLE);
            writeParcelable((Parcelable) v, 0);
        } else if (v instanceof Long) {
            writeInt(VAL_LONG);
            writeLong((Long) v);
        } else if (v instanceof Float) {
            writeInt(VAL_FLOAT);
            writeFloat((Float) v);
        } else if (v instanceof Double) {
            writeInt(VAL_DOUBLE);
            writeDouble((Double) v);
        } else if (v instanceof Boolean) {
            writeInt(VAL_BOOLEAN);
            writeBoolean((Boolean) v);
        } else if (v instanceof List) {
            writeInt(VAL_LIST);
            writeList((List) v);
        } else if (v instanceof byte[]) {
            writeInt(VAL_BYTEARRAY);
            writeByteArray((byte[]) v);
        } else if (v instanceof String[]) {
            writeInt(VAL_STRINGARRAY);
            writeStringList(Arrays.asList((String[]) v));
        } else if (v instanceof Byte) {
            writeInt(VAL_BYTE);
            writeByte((Byte) v);
        } else if (v instanceof Serializable) {
            writeInt(VAL_SERIALIZABLE);
            writeSerializable((Serializable) v);
        } else {
            throw new IllegalArgumentException("Parcel: unable to marshal value " + v);
        }
    }

    /**
     * Writes an exception for {@link #readException()} to rethrow: a code for the exception types Android transports
     * (SecurityException, IllegalArgumentException, NullPointerException, IllegalStateException and
     * UnsupportedOperationException) followed by the message. Any other exception is thrown here instead, like Android
     * does, wrapped in a RuntimeException unless it is one.
     */
    public void writeException(@NonNull Exception e) {
        int code = exceptionCode(e);
        if (code == 0) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
        writeInt(code);
        writeString(e.getMessage());
    }

    /** Writes the code that tells {@link #readException()} that no exception occurred. */
    public void writeNoException() {
        writeInt(0);
    }

    /** Rethrows the exception {@link #writeException} wrote, if one was written at the current position. */
    public void readException() {
        int code = readExceptionCode();
        if (code != 0) {
            readException(code, readString());
        }
    }

    /** The code of the exception written at the current position, or 0 when {@link #writeNoException()} was. */
    public int readExceptionCode() {
        return readInt();
    }

    /** Throws the exception {@code code} stands for, with {@code msg}. */
    public void readException(int code, String msg) {
        switch (code) {
            case EX_SECURITY:
                throw new SecurityException(msg);
            case EX_ILLEGAL_ARGUMENT:
                throw new IllegalArgumentException(msg);
            case EX_NULL_POINTER:
                throw new NullPointerException(msg);
            case EX_ILLEGAL_STATE:
                throw new IllegalStateException(msg);
            case EX_UNSUPPORTED_OPERATION:
                throw new UnsupportedOperationException(msg);
            default:
                throw new RuntimeException("Unknown exception code: " + code + " msg " + msg);
        }
    }

    public byte readByte() {
        return (byte) readInt();
    }

    public int readInt() {
        return read(Integer.class);
    }

    public long readLong() {
        return read(Long.class);
    }

    public float readFloat() {
        return read(Float.class);
    }

    public double readDouble() {
        return read(Double.class);
    }

    public boolean readBoolean() {
        return readInt() != 0;
    }

    @Nullable
    public String readString() {
        return readNullable(String.class);
    }

    /** The same as {@link #readString}, reading what either string method wrote. */
    @Nullable
    public String readString8() {
        return readNullable(String.class);
    }

    @Nullable
    public byte[] createByteArray() {
        byte[] bytes = readNullable(byte[].class);
        return bytes == null ? null : bytes.clone();
    }

    /** Reads an array of the same length as {@code val} into it. */
    public void readByteArray(@NonNull byte[] val) {
        byte[] bytes = createByteArray();
        if (bytes == null || bytes.length != val.length) {
            throw new IllegalStateException("bad array lengths");
        }
        System.arraycopy(bytes, 0, val, 0, val.length);
    }

    @Nullable
    public ArrayList<String> createStringArrayList() {
        int n = readInt();
        if (n < 0) {
            return null;
        }
        ArrayList<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(readString());
        }
        return list;
    }

    /** Replaces the contents of {@code list} with the strings {@link #writeStringList} wrote. */
    public void readStringList(@NonNull List<String> list) {
        list.clear();
        int n = readInt();
        for (int i = 0; i < n; i++) {
            list.add(readString());
        }
    }

    @Nullable
    public ArrayList readArrayList(@Nullable ClassLoader loader) {
        int n = readInt();
        if (n < 0) {
            return null;
        }
        ArrayList<Object> list = new ArrayList<>(n);
        readValues(n, list, loader);
        return list;
    }

    /** Adds the elements {@link #writeList} wrote to {@code outVal}. */
    @SuppressWarnings("unchecked")
    public void readList(@NonNull List outVal, @Nullable ClassLoader loader) {
        readValues(readInt(), outVal, loader);
    }

    @Nullable
    public HashMap readHashMap(@Nullable ClassLoader loader) {
        int n = readInt();
        if (n < 0) {
            return null;
        }
        HashMap<Object, Object> map = new HashMap<>(n);
        readEntries(n, map, loader);
        return map;
    }

    /** Adds the entries {@link #writeMap} wrote to {@code outVal}. */
    @SuppressWarnings("unchecked")
    public void readMap(@NonNull Map outVal, @Nullable ClassLoader loader) {
        readEntries(readInt(), outVal, loader);
    }

    /** Adds the entries {@link #writeArrayMap} wrote to {@code outVal}. */
    public void readArrayMap(@NonNull ArrayMap<? super String, Object> outVal, @Nullable ClassLoader loader) {
        int n = readInt();
        for (int i = 0; i < n; i++) {
            outVal.put(readString(), readValue(loader));
        }
    }

    @Nullable
    public Bundle readBundle() {
        return readBundle(null);
    }

    @Nullable
    public Bundle readBundle(@Nullable ClassLoader loader) {
        int n = readInt();
        if (n < 0) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            map.put(readString(), readValue(loader));
        }
        return new Bundle(map);
    }

    /**
     * Creates the Parcelable {@link #writeParcelable} wrote with the {@code CREATOR} of its class, which is loaded
     * through {@code loader} when one is given.
     *
     * @throws IllegalStateException when the class or its CREATOR cannot be found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> T readParcelable(@Nullable ClassLoader loader) {
        String name = readString();
        if (name == null) {
            return null;
        }
        Parcelable.Creator<?> creator;
        try {
            Class<?> type = loader == null ? Class.forName(name) : Class.forName(name, true, loader);
            creator = (Parcelable.Creator<?>) type.getField("CREATOR").get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("Parcelable " + name + " has no public static CREATOR", e);
        }
        if (creator instanceof Parcelable.ClassLoaderCreator) {
            return (T) ((Parcelable.ClassLoaderCreator<?>) creator).createFromParcel(this, loader);
        }
        return (T) creator.createFromParcel(this);
    }

    @Nullable
    public Serializable readSerializable() {
        return readNullable(Serializable.class);
    }

    /** Reads a value {@link #writeValue} wrote, creating a Parcelable through {@code loader} when one is given. */
    @Nullable
    public Object readValue(@Nullable ClassLoader loader) {
        int type = readInt();
        switch (type) {
            case VAL_NULL:
                return null;
            case VAL_STRING:
                return readString();
            case VAL_INTEGER:
                return readInt();
            case VAL_MAP:
                return readHashMap(loader);
            case VAL_BUNDLE:
                return readBundle(loader);
            case VAL_PARCELABLE:
                return readParcelable(loader);
            case VAL_LONG:
                return readLong();
            case VAL_FLOAT:
                return readFloat();
            case VAL_DOUBLE:
                return readDouble();
            case VAL_BOOLEAN:
                return readBoolean();
            case VAL_LIST:
                return readArrayList(loader);
            case VAL_BYTEARRAY:
                return createByteArray();
            case VAL_STRINGARRAY:
                ArrayList<String> strings = createStringArrayList();
                return strings == null ? null : strings.toArray(new String[0]);
            case VAL_BYTE:
                return readByte();
            case VAL_SERIALIZABLE:
                return readSerializable();
            default:
                throw new IllegalStateException("Parcel: unknown type code " + type + " at position " + (position - 1));
        }
    }

    private void write(@Nullable Object value) {
        if (position == values.size()) {
            values.add(value);
        } else {
            values.set(position, value);
        }
        position++;
    }

    @NonNull
    private <T> T read(Class<T> type) {
        T value = readNullable(type);
        if (value == null) {
            throw new IllegalStateException("Parcel: expected a " + type.getSimpleName() + " at position " + (position - 1) + " but read null");
        }
        return value;
    }

    @Nullable
    private <T> T readNullable(Class<T> type) {
        if (position >= values.size()) {
            throw new IllegalStateException("Parcel: no value left to read at position " + position);
        }
        Object value = values.get(position);
        if (value != null && !type.isInstance(value)) {
            throw new IllegalStateException("Parcel: expected a " + type.getSimpleName() + " at position " + position + " but found a " + value.getClass().getSimpleName());
        }
        position++;
        return type.cast(value);
    }

    private void readValues(int n, List<Object> outVal, @Nullable ClassLoader loader) {
        for (int i = 0; i < n; i++) {
            outVal.add(readValue(loader));
        }
    }

    private void readEntries(int n, Map<Object, Object> outVal, @Nullable ClassLoader loader) {
        for (int i = 0; i < n; i++) {
            outVal.put(readValue(loader), readValue(loader));
        }
    }

    private static int exceptionCode(Exception e) {
        if (e instanceof SecurityException) {
            return EX_SECURITY;
        } else if (e instanceof IllegalArgumentException) {
            return EX_ILLEGAL_ARGUMENT;
        } else if (e instanceof NullPointerException) {
            return EX_NULL_POINTER;
        } else if (e instanceof IllegalStateException) {
            return EX_ILLEGAL_STATE;
        } else if (e instanceof UnsupportedOperationException) {
            return EX_UNSUPPORTED_OPERATION;
        }
        return 0;
    }
}
