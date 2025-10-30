package com.example.munchai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NutritionFacts
{
    @JsonProperty("Name") public String name;
    @JsonProperty("Serving size") public String servingSize;
    @JsonProperty("Calories") public Integer calories;
    @JsonProperty("Total Fat (%)") public Integer totalFatPercent;
    @JsonProperty("Saturated Fat (%)") public Integer saturatedFatPercent;
    @JsonProperty("Cholesterol (mg)") public Integer cholesterolMg;
    @JsonProperty("Sodium (mg)") public Integer sodiumMg;
    @JsonProperty("Total Carbohydrate (g)") public Double totalCarbG;
    @JsonProperty("Dietary Fiber (g)") public Double fiberG;
    @JsonProperty("Sugars (g)") public Double sugarsG;
    @JsonProperty("Protein (g)") public Double proteinG;
    @JsonProperty("Vitamin A (%)") public Integer vitaminAPercent;
    @JsonProperty("Vitamin C (%)") public Integer vitaminCPercent;
    @JsonProperty("Calcium (%)") public Integer calciumPercent;
    @JsonProperty("Iron (%)") public Integer ironPercent;

    public String nutritionValuesToString()
    {
        return "Name: " + name + "\n" +
                "Serving: " + servingSize + "\n" +
                "Calories: " + calories + "\n" +
                "Protein: " + proteinG + " g\n" +
                "Carbs: " + totalCarbG + " g (Fiber " + fiberG + " g, Sugar " + sugarsG + " g)\n" +
                "Fat: " + totalFatPercent + "% (Sat " + saturatedFatPercent + "%)\n" +
                "Na: " + sodiumMg + " mg, Chol: " + cholesterolMg + " mg\n" +
                "Vit A " + vitaminAPercent + "%, C " + vitaminCPercent + "%, Ca " + calciumPercent + "%, Fe " + ironPercent + "%";
    }
}