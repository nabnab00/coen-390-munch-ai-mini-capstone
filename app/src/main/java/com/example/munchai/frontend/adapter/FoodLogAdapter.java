package com.example.munchai.frontend.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.munchai.R;
import com.example.munchai.frontend.DisplayLogActivity;
import com.example.munchai.model.FoodLogRow;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class FoodLogAdapter extends RecyclerView.Adapter<FoodLogAdapter.VH>
{

    private final List<FoodLogRow> data = new ArrayList<>();
    private final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    private final SimpleDateFormat out = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public FoodLogAdapter()
    {
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void setItems(List<FoodLogRow> rows)
    {
        data.clear();
        if (rows != null) data.addAll(rows);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.logmodel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos)
    {
        FoodLogRow r = data.get(pos);
        String dateStr = r.loggedAtIso;
        try
        {
            dateStr = out.format(iso.parse(r.loggedAtIso));
        }
        catch (ParseException ignored) {}

        h.top.setText(dateStr + " \u2022 " + r.meal);

        StringBuilder bottom = new StringBuilder();
        bottom.append(r.name)
                .append(" — ")
                .append(trimQty(r.weight))
                .append(" ")
                .append(r.unit);

        if (r.calories != null) {
            bottom.append(" \u2022 ").append(trimQty(r.calories)).append(" kcal");
        }

        StringBuilder macros = new StringBuilder();
        if (r.proteinG != null) { macros.append("Protein ").append(trimQty(r.proteinG)).append("g"); }
        if (r.carbG != null)    { macros.append(" \u2022 "); macros.append("Carbs ").append(trimQty(r.carbG)).append("g"); }
        if (r.fatG != null)     {macros.append(" \u2022 "); macros.append("Fat ").append(trimQty(r.fatG)).append("g"); }

        bottom.append(" \u2022 ").append(macros);

        h.bottom.setText(bottom.toString());

        h.itemView.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, DisplayLogActivity.class);
            intent.putExtra("food_log", r);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount()
    {
        return data.size();
    }

    static String trimQty(double q)
    {
        if (Math.abs(q - Math.round(q)) < 1e-9) return String.valueOf((long) q);
        return String.valueOf(q);
    }

    static class VH extends RecyclerView.ViewHolder
    {
        final TextView top, bottom;
        VH(@NonNull View itemView)
        {
            super(itemView);
            top = itemView.findViewById(R.id.row_top);
            bottom = itemView.findViewById(R.id.row_bottom);
        }
    }
}
