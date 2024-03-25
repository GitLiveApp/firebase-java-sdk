package org.sqlite.core

import android.database.CursorWindow
import android.database.sqlite.SQLiteCustomFunction
import android.database.sqlite.SQLiteDatabase
import org.sqlite.Collation
import org.sqlite.Function
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteOpenMode
import java.text.Collator
import java.util.*

fun Open(path: String, openFlags: Int, label: String, enableTrace: Boolean, enableProfile: Boolean): NativeDB {
    NativeDB.load()
    val db = NativeDB(null, path, SQLiteConfig())
    val flags = (0 .. 31).asSequence()
        .map { 1 shl it }
        .filter { it and openFlags > 0 }
        .map {
            when(it) {
                SQLiteDatabase.CREATE_IF_NECESSARY -> SQLiteOpenMode.READWRITE.flag or SQLiteOpenMode.CREATE.flag
                SQLiteDatabase.OPEN_READONLY -> {
                    db.config.isExplicitReadOnly = true
                    SQLiteOpenMode.READONLY.flag
                }
                SQLiteDatabase.OPEN_READWRITE -> SQLiteOpenMode.READWRITE.flag
                else -> TODO("Unknown openFlag ${it.toString(16)}")
            }
        }
        .reduce { acc, flag -> acc or flag }
    db.open(path, flags)
    return db
}

fun Close(connectionPtr: NativeDB) = connectionPtr._close()

fun RegisterCustomFunction(
    connectionPtr: NativeDB,
    function: SQLiteCustomFunction
) {
    val callback = object : Function() {
        override fun xFunc() {
            val args = arrayOfNulls<String>(args())
            args.indices.forEach { args[it] = value_text(it) }
//            function.callback.callback(args)
        }

    }
    connectionPtr.create_function(function.name, callback, function.numArgs, 0)
}

fun RegisterLocalizedCollators(connectionPtr: NativeDB, locale: String) {
    val collator = Collator.getInstance(Locale.forLanguageTag(locale))
    connectionPtr.create_collation(locale, object : Collation() {
        override fun xCompare(str1: String?, str2: String?) = collator.compare(str1, str2)
    })
}

fun PrepareStatement(connectionPtr: NativeDB, sql: String) =
    connectionPtr.prepare_utf8(NativeDB.stringToUtf8ByteArray(sql))

fun FinalizeStatement(connectionPtr: NativeDB, statementPtr: Long) = connectionPtr.finalize(statementPtr)

fun GetParameterCount(connectionPtr: NativeDB, statementPtr: Long): Int = connectionPtr.bind_parameter_count(statementPtr)

fun IsReadOnly(connectionPtr: NativeDB, statementPtr: Long): Boolean = connectionPtr.config.isExplicitReadOnly

fun GetColumnCount(connectionPtr: NativeDB, statementPtr: Long): Int = connectionPtr.column_count(statementPtr)

fun GetColumnName(connectionPtr: NativeDB, statementPtr: Long, index: Int): String? =
    connectionPtr.column_name(statementPtr, index)

fun BindNull(connectionPtr: NativeDB, statementPtr: Long, index: Int) = connectionPtr.bind_null(statementPtr, index)

fun BindLong(connectionPtr: NativeDB, statementPtr: Long, index: Int, value: Long) =
    connectionPtr.bind_long(statementPtr, index, value)

fun BindDouble(connectionPtr: NativeDB, statementPtr: Long, index: Int, value: Double) =
    connectionPtr.bind_double(statementPtr, index, value)

fun BindString(connectionPtr: NativeDB, statementPtr: Long, index: Int, value: String) =
    connectionPtr.bind_text(statementPtr, index, value)

fun BindBlob(connectionPtr: NativeDB, statementPtr: Long, index: Int, value: ByteArray) =
    connectionPtr.bind_blob(statementPtr, index, value)

fun ResetStatementAndClearBindings(connectionPtr: NativeDB, statementPtr: Long) {
    connectionPtr.reset(statementPtr)
    connectionPtr.clear_bindings(statementPtr)
}

fun Execute(connectionPtr: NativeDB, statementPtr: Long) = connectionPtr.step(statementPtr)

fun ExecuteForLong(connectionPtr: NativeDB, statementPtr: Long): Long {
    connectionPtr.step(statementPtr)
    return connectionPtr.column_long(statementPtr, 0)
}

fun ExecuteForString(connectionPtr: NativeDB, statementPtr: Long): String? {
    connectionPtr.step(statementPtr)
    return connectionPtr.column_text(statementPtr, 0)
}

fun GetDbLookaside(connectionPtr: NativeDB): Int = 0

fun Cancel(connectionPtr: NativeDB): Nothing = TODO()

fun ResetCancel(connectionPtr: NativeDB, cancelable: Boolean): Nothing = TODO()

fun HasCodec(): Boolean = false

fun ExecuteForBlobFileDescriptor(connectionPtr: NativeDB, statementPtr: Long): Int = TODO()

fun ExecuteForChangedRowCount(connectionPtr: NativeDB, statementPtr: Long): Int {
    connectionPtr.step(statementPtr)
    return connectionPtr.changes().toInt()
}

fun ExecuteForLastInsertedRowId(connectionPtr: NativeDB, statementPtr: Long): Long {
    connectionPtr.step(statementPtr)
    return connectionPtr.column_long(statementPtr, 0)
}

fun ExecuteForCursorWindow(connectionPtr: NativeDB, statementPtr: Long, win: CursorWindow, startPos: Int, iRowRequired: Int, countAllRows: Boolean): Long {

    /* Set the number of columns in the window */
    if(!win.setNumColumns(connectionPtr.column_count(statementPtr))) return 0

    var nRow = 0;
    var iStart = startPos;
    var bOk = true;

    while(connectionPtr.step(statementPtr) == Codes.SQLITE_ROW) {
        /* Only copy in rows that occur at or after row index iStart. */
        if((nRow >= iStart) && bOk){
            bOk = copyRowToWindow(connectionPtr, win, (nRow - iStart), statementPtr);
            if(!bOk){
                /* The CursorWindow object ran out of memory. If row iRowRequired was
                ** not successfully added before this happened, clear the CursorWindow
                ** and try to add the current row again.  */
                if( nRow<=iRowRequired ){
                    bOk = win.setNumColumns(connectionPtr.column_count(statementPtr));
                    if(!bOk){
                        connectionPtr.reset(statementPtr);
                        return 0;
                    }
                    iStart = nRow;
                    bOk = copyRowToWindow(connectionPtr, win, (nRow - iStart), statementPtr);
                }

                /* If the CursorWindow is still full and the countAllRows flag is not
                ** set, break out of the loop here. If countAllRows is set, continue
                ** so as to set variable nRow correctly.  */
                if( !bOk && !countAllRows ) break;
            }
        }

        nRow++;
    }

    /* Finalize the statement. If this indicates an error occurred, throw an
    ** SQLiteException exception.  */
    val rc = connectionPtr.reset(statementPtr);
    if( rc!= Codes.SQLITE_OK ){
        NativeDB.throwex(rc, connectionPtr.errmsg())
        return 0;
    }

    return iStart.toLong() shl 32 or nRow.toLong();
}

/*
** Append the contents of the row that SQL statement pStmt currently points to
** to the CursorWindow object passed as the second argument. The CursorWindow
** currently contains iRow rows. Return true on success or false if an error
** occurs.
*/
fun copyRowToWindow(connectionPtr: NativeDB, win: CursorWindow, iRow: Int, statementPtr: Long): Boolean {
    val nCol = connectionPtr.column_count(statementPtr);
    val i = 0
    var bOk = false

    bOk = win.allocRow()
    for(i in 0 until nCol){
        when(val type = connectionPtr.column_type(statementPtr, i)) {
            Codes.SQLITE_NULL -> win.putNull(iRow, i)
            Codes.SQLITE_INTEGER -> win.putLong(connectionPtr.column_long(statementPtr, i), iRow, i)
            Codes.SQLITE_FLOAT -> win.putDouble(connectionPtr.column_double(statementPtr, i), iRow, i)
            Codes.SQLITE_TEXT -> win.putString(connectionPtr.column_text(statementPtr, i), iRow, i)
            Codes.SQLITE_BLOB -> win.putBlob(connectionPtr.column_blob(statementPtr, i), iRow, i)
            else -> TODO("Unknown column type: $type")
        }

        if(!bOk) win.freeLastRow()
    }

    return bOk;
}
