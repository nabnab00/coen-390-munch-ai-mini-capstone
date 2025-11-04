package com.example.munchai.model;

public class FoodLogRow
{
    public final String name, unit, meal, loggedAtIso;
    public final double qty;

    public FoodLogRow(String name, String unit, double qty, String meal, String loggedAtIso)
    {
        this.name = name;
        this.unit = unit;
        this.qty = qty;
        this.meal = meal;
        this.loggedAtIso = loggedAtIso;
    }
}
