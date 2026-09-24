package com.keyboards10

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ClipItem(val id: Long, val text: String, val pinned: Boolean, val created: Long)

class ClipboardDb(context: Context) : SQLiteOpenHelper(context, "clipboard.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE clips(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "text TEXT NOT NULL," +
                "pinned INTEGER NOT NULL DEFAULT 0," +
                "created INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_clips_created ON clips(created DESC)")
        db.execSQL("CREATE INDEX idx_clips_pinned ON clips(pinned DESC, created DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun add(text: String) {
        if (text.isEmpty()) return
        val db = writableDatabase
        val c = db.query("clips", arrayOf("id", "pinned"), "text=?", arrayOf(text),
            null, null, "created DESC", "1")
        val exists = c.use { if (it.moveToFirst()) Pair(it.getLong(0), it.getInt(1) == 1) else null }
        if (exists != null) {
            val v = ContentValues().apply { put("created", System.currentTimeMillis()) }
            db.update("clips", v, "id=?", arrayOf(exists.first.toString()))
        } else {
            val v = ContentValues().apply {
                put("text", text)
                put("pinned", 0)
                put("created", System.currentTimeMillis())
            }
            db.insert("clips", null, v)
        }
    }

    @Synchronized fun all(): List<ClipItem> {
        val out = ArrayList<ClipItem>()
        readableDatabase.query(
            "clips", arrayOf("id", "text", "pinned", "created"),
            null, null, null, null, "pinned DESC, created DESC"
        ).use { c ->
            while (c.moveToNext()) {
                out += ClipItem(c.getLong(0), c.getString(1), c.getInt(2) == 1, c.getLong(3))
            }
        }
        return out
    }

    @Synchronized fun setPinned(id: Long, value: Boolean) {
        val v = ContentValues().apply { put("pinned", if (value) 1 else 0) }
        writableDatabase.update("clips", v, "id=?", arrayOf(id.toString()))
    }

    @Synchronized fun delete(id: Long) {
        writableDatabase.delete("clips", "id=?", arrayOf(id.toString()))
    }
}
