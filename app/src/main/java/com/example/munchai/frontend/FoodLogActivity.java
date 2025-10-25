package com.example.munchai.frontend;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.munchai.R;
import com.example.munchai.backend.AppDatabaseHelper;
import com.example.munchai.backend.SessionManager;
import com.example.munchai.frontend.adapter.FoodLogAdapter;
import com.example.munchai.frontend.model.FoodLogRow;

import java.util.ArrayList;
import java.util.List;

public class FoodLogActivity extends AppCompatActivity
{
    private AppDatabaseHelper db;
    private SessionManager session;
    private FoodLogAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.foodlogpage);

        db = new AppDatabaseHelper(this);
        session = new SessionManager(this);

        if (!session.isLoggedIn())
        {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        RecyclerView rv = findViewById(R.id.logs_list);
        empty = findViewById(R.id.empty_state);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FoodLogAdapter();
        rv.setAdapter(adapter);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        loadLogs();
    }

    private void loadLogs()
    {
        List<FoodLogRow> rows = new ArrayList<>();
        Cursor c = db.getLogsForUser(session.getLoggedInUserId());
        if (c != null)
        {
            int idxName = c.getColumnIndexOrThrow(AppDatabaseHelper.COL_LOG_NAME);
            int idxUnit = c.getColumnIndexOrThrow(AppDatabaseHelper.COL_LOG_UNIT);
            int idxQty  = c.getColumnIndexOrThrow(AppDatabaseHelper.COL_LOG_QTY);
            int idxMeal = c.getColumnIndexOrThrow(AppDatabaseHelper.COL_LOG_MEAL);
            int idxAt   = c.getColumnIndexOrThrow(AppDatabaseHelper.COL_LOG_AT);

            while (c.moveToNext())
            {
                rows.add(new FoodLogRow(
                        c.getString(idxName),
                        c.getString(idxUnit),
                        c.getDouble(idxQty),
                        c.getString(idxMeal),
                        c.getString(idxAt)
                ));
            }
            c.close();
        }

        adapter.setItems(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
