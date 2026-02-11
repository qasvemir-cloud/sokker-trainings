package org.velja.app.model;

import lombok.Data;

@Data
public class Characteristics {
    private double weight;
    private int age;
    private int height;
    private double bmi;

    public Characteristics(int age, int height, double bmi, double weight) {
        this.age = age;
        this.height = height;
        this.bmi = bmi;
        this.weight = weight;
    }
}
