package com.example.ioslauncher

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class LauncherDb(context: Context) : SQLiteOpenHelper(context, "launcher_state.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, detail TEXT, created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun put(key: String, value: String) {
        val values = ContentValues().apply { put("k", key); put("v", value) }
        writableDatabase.insertWithOnConflict("kv", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun get(key: String, fallback: String = ""): String {
        readableDatabase.rawQuery("SELECT v FROM kv WHERE k=?", arrayOf(key)).use {
            return if (it.moveToFirst()) it.getString(0) else fallback
        }
    }
    fun log(type: String, detail: String = "") {
        val values = ContentValues().apply {
            put("type", type); put("detail", detail); put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("events", null, values)
    }
}
