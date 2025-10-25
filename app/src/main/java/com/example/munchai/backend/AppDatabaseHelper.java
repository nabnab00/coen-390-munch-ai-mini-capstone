package com.example.munchai.backend;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

public class AppDatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "munchai.db";
    public static final int DB_VERSION = 2;

    //Users
    public static final String T_USERS = "users";
    public static final String COL_USER_ID = "id";
    public static final String COL_USERNAME = "username";
    public static final String COL_PASSWORD = "password";

    //Food logs
    public static final String T_LOGS = "food_logs";
    public static final String COL_LOG_ID = "id";
    public static final String COL_LOG_USER_ID = "user_id";
    public static final String COL_LOG_NAME = "name";
    public static final String COL_LOG_UNIT = "unit";   //  g,oz,ml
    public static final String COL_LOG_QTY = "qty";     // double/real
    public static final String COL_LOG_MEAL = "meal";
    public static final String COL_LOG_AT = "logged_at";

    public AppDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_USERS + " (" +
                COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USERNAME + " TEXT UNIQUE NOT NULL, " +
                COL_PASSWORD + " TEXT NOT NULL" +
                ");");

        db.execSQL("CREATE TABLE " + T_LOGS + " (" +
                COL_LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_LOG_USER_ID + " INTEGER NOT NULL, " +
                COL_LOG_NAME + " TEXT NOT NULL, " +
                COL_LOG_UNIT + " TEXT NOT NULL, " +
                COL_LOG_QTY + " REAL NOT NULL, " +
                COL_LOG_MEAL + " TEXT NOT NULL, " +
                COL_LOG_AT + " TEXT NOT NULL, " +
                "FOREIGN KEY(" + COL_LOG_USER_ID + ") REFERENCES " + T_USERS + "(" + COL_USER_ID + ")" +
                ");");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_LOGS);
        db.execSQL("DROP TABLE IF EXISTS " + T_USERS);
        onCreate(db);
    }

    //USERS
    public long createUser(String username, String password) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_USERNAME, username);
        cv.put(COL_PASSWORD, password);
        return db.insert(T_USERS, null, cv);
    }

    public boolean usernameExists(String username) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_USERS, new String[]{COL_USER_ID}, COL_USERNAME + "=?",
                new String[]{username}, null, null, null);
        boolean exists = c != null && c.moveToFirst();
        if (c != null) c.close();
        return exists;
    }

    public Integer authenticate(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(T_USERS, new String[]{COL_USER_ID},
                COL_USERNAME + "=? AND " + COL_PASSWORD + "=?",
                new String[]{username, password}, null, null, null);
        Integer userId = null;
        if (c != null) {
            if (c.moveToFirst()) userId = c.getInt(0);
            c.close();
        }
        return userId;
    }

    // FOOD LOGS
    public long insertLog(int userId, String name, String unit, double qty, String meal, String isoTimestamp) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LOG_USER_ID, userId);
        cv.put(COL_LOG_NAME, name);
        cv.put(COL_LOG_UNIT, unit);
        cv.put(COL_LOG_QTY, qty);
        cv.put(COL_LOG_MEAL, meal);
        cv.put(COL_LOG_AT, isoTimestamp);
        return db.insert(T_LOGS, null, cv);
    }

    public Cursor getLogsForUser(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(
                T_LOGS,
                null,
                COL_LOG_USER_ID + "=?",
                new String[]{String.valueOf(userId)},
                null,
                null,
                COL_LOG_AT + " DESC"
        );
    }
}
