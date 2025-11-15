package com.example.munchai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NutritionFacts
{
    @JsonProperty("name") public String name;
    @JsonProperty("serving_size") public String servingSize;
    @JsonProperty("calories") public Double calories;
    @JsonProperty("total_fat_g") public Double totalFatG;
    @JsonProperty("saturated_fat_g") public Double saturatedFatG;
    @JsonProperty("cholesterol_mg") public Double cholesterolMg;
    @JsonProperty("sodium_mg") public Double sodiumMg;
    @JsonProperty("total_carbohydrate_g") public Double totalCarbG;
    @JsonProperty("dietary_fiber_g") public Double fiberG;
    @JsonProperty("sugars_g") public Double sugarsG;
    @JsonProperty("protein_g") public Double proteinG;
    @JsonProperty("vitamin_a_percent") public Double vitaminAPercent;
    @JsonProperty("vitamin_c_percent") public Double vitaminCPercent;
    @JsonProperty("calcium_percent") public Double calciumPercent;
    @JsonProperty("iron_percent") public Double ironPercent;

    public String nutritionValuesToString() {
        return "Name: " + safe(name) + "\n" +
                "Serving: " + safe(servingSize) + "\n" +
                "Calories: " + safe(calories) + "\n" +
                "Protein: " + safe(proteinG) + " g\n" +
                "Carbs: " + safe(totalCarbG) + " g\n" +
                "Fiber: " + safe(fiberG) + " g\n" +
                "Sugars: " + safe(sugarsG) + " g\n" +
                "Fat: " + safe(totalFatG) + " g\n" +
                "Sodium: " + safe(sodiumMg) + " mg\n" +
                "Cholesterol: " + safe(cholesterolMg) + " mg\n";
    }

    private String safe(Object v) { return (v == null) ? "N/A" : v.toString(); }
}
