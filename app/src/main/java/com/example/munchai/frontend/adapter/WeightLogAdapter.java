package com.example.munchai.frontend.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.munchai.R;
import com.example.munchai.model.WeightLog;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

// 1. Extend RecyclerView.Adapter instead of ArrayAdapter
public class WeightLogAdapter extends RecyclerView.Adapter<WeightLogAdapter.WeightLogViewHolder> {

    private final Context mContext;
    private final List<WeightLog> mWeightLogs;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    // 2. ViewHolder class to hold the views for each item
    public static class WeightLogViewHolder extends RecyclerView.ViewHolder {
        public TextView dateTextView;
        public TextView weightTextView;

        public WeightLogViewHolder(@NonNull View itemView) {
            super(itemView);
            dateTextView = itemView.findViewById(android.R.id.text1);
            weightTextView = itemView.findViewById(android.R.id.text2);
        }
    }

    // 3. Update the constructor
    public WeightLogAdapter(Context context, List<WeightLog> weightLogs) {
        mContext = context;
        mWeightLogs = weightLogs;
    }

    // 4. Implement required RecyclerView.Adapter methods
    @NonNull
    @Override
    public WeightLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate a standard Android layout for simplicity. You can create a custom one.
        View view = LayoutInflater.from(mContext).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new WeightLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WeightLogViewHolder holder, int position) {
        WeightLog currentLog = mWeightLogs.get(position);

        // Bind data to the views
        holder.dateTextView.setText(dateFormat.format(currentLog.getDate()));
        holder.weightTextView.setText(String.format(Locale.getDefault(), "%.1f kg", currentLog.getWeight()));
    }

    @Override
    public int getItemCount() {
        return mWeightLogs.size();
    }
}
