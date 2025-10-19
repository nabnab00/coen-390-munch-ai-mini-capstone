package com.example.munchai.model;

public class LogEntry {
    public String foodName;
    public double weight;
    public String unit;     // "g", "oz", "ml"
    public String meal;     // "Breakfast", "Lunch", "Snack", "Dinner"
    public long timestamp;  // epoch millis

    public LogEntry(String foodName, double weight, String unit, String meal, long timestamp) {
        this.foodName = foodName;
        this.weight = weight;
        this.unit = unit;
        this.meal = meal;
        this.timestamp = timestamp;
    }
}
