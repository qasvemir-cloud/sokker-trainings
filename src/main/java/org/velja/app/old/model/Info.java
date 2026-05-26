package org.velja.app.old.model;

import lombok.Data;

@Data
public class Info {
    private Country country;
    private Characteristics characteristics;
    private Country formation;
    private PreviousValue previousValue;
    private Skills skills;
    private long youthTeamId;
    private long number;
    private boolean nationalSharing;
    private Face face;
    private Stats nationalStats;
    private Stats stats;
    private Name name;
    private Skills skillsChange;
    private Injury injury;
    private PreviousValue value;
    private PreviousValue wage;

    public Info(Name name,Skills skills, Characteristics characteristics)
    {
        this.name=name;
        this.skills=skills;
        this.characteristics=characteristics;
    }


}