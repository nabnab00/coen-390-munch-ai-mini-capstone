package com.example.munchai.backend.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

public class SettingsDatabaseHelper extends SQLiteOpenHelper
{
    private static final String DATABASE_NAME = "settings.db";
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
    public void onCreate(SQLiteDatabase db)
    {
        String createTable = "CREATE TABLE " + TABLE_SETTINGS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_MODE + " INTEGER DEFAULT 0, " +
                COLUMN_CALORIES + " INTEGER DEFAULT 2000, " +
                COLUMN_PROTEIN + " INTEGER DEFAULT 100, " +
                COLUMN_CARBS + " INTEGER DEFAULT 250, " +
                COLUMN_FAT + " INTEGER DEFAULT 70)";
        db.execSQL(createTable);

        // default row
        ContentValues values = new ContentValues();
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

        // Check if a settings row exists
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_SETTINGS + " LIMIT 1", null);
        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
            db.update(TABLE_SETTINGS, values, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        } else {
            db.insert(TABLE_SETTINGS, null, values);
        }
        if (cursor != null) cursor.close();
        db.close();
    }

    public Cursor getSettings()
    {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_SETTINGS + " WHERE " + COLUMN_ID + " = 1", null);
    }
}

