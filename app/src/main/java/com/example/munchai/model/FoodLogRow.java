package com.example.munchai.model;

public class FoodLogRow
{
    public final String name, unit, meal, loggedAtIso;
    public final double weight;
    public final Double calories;
    public final Double fatG;
    public final Double proteinG;
    public final Double carbG;


    public FoodLogRow(String name, String unit, double weight, String meal, String loggedAtIso,
                      Double calories, Double fatG, Double proteinG, Double carbG)
    {
        this.name = name;
        this.unit = unit;
        this.weight = weight;
        this.meal = meal;
        this.loggedAtIso = loggedAtIso;
        this.calories = calories;
        this.fatG = fatG;
        this.proteinG = proteinG;
        this.carbG = carbG;

    }
}
