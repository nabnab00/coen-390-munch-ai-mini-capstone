package com.example.munchai.backend.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

public class SettingsDatabaseHelper extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "app_database.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_SETTINGS = "user_settings";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_MODE = "dark_mode"; // 0 = light, 1 = dark
    private static final String COLUMN_CALORIES = "calorie_limit";
    private static final String COLUMN_PROTEIN = "protein_limit";
    private static final String COLUMN_CARBS = "carb_limit";
    private static final String COLUMN_FAT = "fat_limit";

    public SettingsDatabaseHelper(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SETTINGS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_MODE + " INTEGER, " +
                COLUMN_CALORIES + " INTEGER, " +
                COLUMN_PROTEIN + " INTEGER, " +
                COLUMN_CARBS + " INTEGER, " +
                COLUMN_FAT + " INTEGER)");

        // Insert default row so app always has settings
        ContentValues values = new ContentValues();
        values.put(COLUMN_MODE, 0);
        values.put(COLUMN_CALORIES, 2000);
        values.put(COLUMN_PROTEIN, 50);
        values.put(COLUMN_CARBS, 250);
        values.put(COLUMN_FAT, 70);
        db.insert(TABLE_SETTINGS, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
        onCreate(db);
    }

    public void updateSettings(int mode, int calories, int protein, int carbs, int fat) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_MODE, mode);
        values.put(COLUMN_CALORIES, calories);
        values.put(COLUMN_PROTEIN, protein);
        values.put(COLUMN_CARBS, carbs);
        values.put(COLUMN_FAT, fat);

        Cursor cursor = db.rawQuery("SELECT " + COLUMN_ID + " FROM " + TABLE_SETTINGS + " LIMIT 1", null);

        if (cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
            db.update(TABLE_SETTINGS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        } else {
            // No row exists, insert a new one
            db.insert(TABLE_SETTINGS, null, values);
        }

        cursor.close();
        db.close();
    }

    public Cursor getSettings() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_SETTINGS + " LIMIT 1", null);
    }
}

