package org.velja.app.old.model;

import lombok.Data;

@Data
public class Skills
{
    int form;
    int tacticalDiscipline;
    int teamwork;
    int experience;
    int stamina;
    int keeper;
    int playmaking;
    int passing;
    int technique;
    int defending;
    int striker;
    int pace;
    int down;
    int up;

    public Skills(int form, int tacticalDiscipline, int experience, int teamwork, int stamina,
                  int pace, int striker, int defending, int technique, int passing,
                  int playmaking, int keeper) {
        this.form = form;
        this.tacticalDiscipline = tacticalDiscipline;
        this.experience = experience;
        this.teamwork = teamwork;
        this.stamina = stamina;
        this.pace = pace;
        this.striker = striker;
        this.defending = defending;
        this.technique = technique;
        this.passing = passing;
        this.playmaking = playmaking;
        this.keeper = keeper;
    }
    public Skills()
    {}
}
