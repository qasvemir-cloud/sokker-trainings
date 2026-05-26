package org.velja.app.old.model;

import lombok.Data;

@Data
public class Team {
    private int id;
    private String name;
    private double rank;
    private int rankPosition;
    private String emblem;
    private String country;


    public Team(int id, String name, double rank, int rankPosition, String emblem, String country) {
        this.id = id;
        this.name = name;
        this.rank = rank;
        this.rankPosition = rankPosition;
        this.emblem = emblem;
        this.country = country;

    }

}