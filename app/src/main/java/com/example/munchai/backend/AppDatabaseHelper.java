package com.example.munchai.backend;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;import android.database.Cursor;

public class AppDatabaseHelper extends SQLiteOpenHelper
{
    public static final String DB_NAME = "munchai.db";
    public static final int DB_VERSION = 4; // Incremented DB_VERSION to trigger onUpgrade

    //Food logs
    public static final String T_LOGS = "food_logs";
    public static final String COL_LOG_ID = "id";
    public static final String COL_LOG_NAME = "name";
    public static final String COL_LOG_QTY = "qty";     // double/real, now represents weight
    public static final String COL_LOG_CALORIES = "calories";
    public static final String COL_LOG_FAT = "fat";
    public static final String COL_LOG_PROTEIN = "protein";
    public static final String COL_LOG_CARBS = "carbs";
    public static final String COL_LOG_MEAL = "meal";
    public static final String COL_LOG_AT = "logged_at";

    public AppDatabaseHelper(Context context)
    {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE " + T_LOGS + " (" +
                COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_LOG_NAME + " TEXT NOT NULL, " +
                COL_LOG_QTY + " REAL NOT NULL, " +
                COL_LOG_CALORIES + " REAL NOT NULL, " +
                COL_LOG_FAT + " REAL NOT NULL, " +
                COL_LOG_PROTEIN + " REAL NOT NULL, " +
                COL_LOG_CARBS + " REAL NOT NULL, " +
                COL_LOG_MEAL + " TEXT NOT NULL, " +
                COL_LOG_AT + " TEXT NOT NULL " + ");");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_LOGS);
        onCreate(db);
    }

    // FOOD LOGS
    public long insertLog(String name, double qty, double calories, double fat, double protein, double carbs, String meal, String isoTimestamp) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOG_NAME, name);
        cv.put(COL_LOG_QTY, qty);
        cv.put(COL_LOG_CALORIES, calories);
        cv.put(COL_LOG_FAT, fat);
        cv.put(COL_LOG_PROTEIN, protein);
        cv.put(COL_LOG_CARBS, carbs);
        cv.put(COL_LOG_MEAL, meal);
        cv.put(COL_LOG_AT, isoTimestamp);
        return db.insert(T_LOGS, null, cv);
    }

    public Cursor getAllLogs() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(
                T_LOGS,
                null,
                null,
                null,
                null,
                null,
                COL_LOG_AT + " DESC"
        );
    }

    public void clearLogs(){
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_LOGS, null, null);
    }
}
