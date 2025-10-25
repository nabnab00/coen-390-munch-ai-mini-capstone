package com.example.munchai.frontend;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.munchai.R;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

class FoodLogRow
{
    public final String name, unit, meal, loggedAtIso;
    public final double qty;
    FoodLogRow(String name, String unit, double qty, String meal, String loggedAtIso)
    {
        this.name = name; this.unit = unit; this.qty = qty; this.meal = meal; this.loggedAtIso = loggedAtIso;
    }
}

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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_food_log, parent, false);
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
        h.bottom.setText(r.name + " — " + trimQty(r.qty) + " " + r.unit);
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
