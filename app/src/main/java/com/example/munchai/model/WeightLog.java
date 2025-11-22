
package com.example.munchai.model;

import java.util.Date;

public class WeightLog {
    private Date date;
    private double weight;

    public WeightLog() {
    }

    public WeightLog(Date date, double weight) {
        this.date = date;
        this.weight = weight;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
}
