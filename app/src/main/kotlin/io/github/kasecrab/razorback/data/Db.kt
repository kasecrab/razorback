package io.github.kasecrab.razorback.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** One SQLite file, WAL, foreign keys on. All writes go through [writer] so they never interleave. */
class Db(context: Context) : SQLiteOpenHelper(context, "razorback.db", null, Schema.VERSION) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val writer: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        for (v in Schema.versions) for (sql in v) db.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (v in oldVersion until newVersion) for (sql in Schema.versions[v]) db.execSQL(sql)
    }

    inline fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val r = block(db)
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }
}
