package com.example.munchai.model;

import java.io.Serializable;

public class FoodLogRow implements Serializable {

    public final String name;
    public final String unit;
    public final double weight;
    public final String meal;
    public final String loggedAtIso;
    public final Double calories;
    public final Double fatG;
    public final Double proteinG;
    public final Double carbG;
    public final String imageUrl;
    public String documentId;

    public FoodLogRow(String name, String unit, Double weight, String meal,
                      String loggedAtIso, Double calories, Double fatG,
                      Double proteinG, Double carbG, String imageUrl,
                      String documentId) {
        this.name = name;
        this.unit = unit;
        this.weight = weight;
        this.meal = meal;
        this.loggedAtIso = loggedAtIso;
        this.calories = calories;
        this.fatG = fatG;
        this.proteinG = proteinG;
        this.carbG = carbG;
        this.imageUrl = imageUrl;
        this.documentId = documentId;
    }
}
