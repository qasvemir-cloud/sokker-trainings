package org.velja.app.model;

import lombok.Data;
import java.util.List;

@Data
public class TrainingReport {
    private List<TrainingEntry> reports;

    @Data
    public static class TrainingEntry {
        private int week;
        private TrainingDay day;
        private Skills skills;
        private Skills skillsChange;
        private TrainingType type;
        private TrainingKind kind;
        private PlayerValue playerValue;
        private Games games;
        private int intensity;
        private Formation formation;
        private Injury injury;
        public TrainingEntry(TrainingDay day, Skills skills, Skills skillsChange, TrainingType trainingType)
        {
            this.day=day;
            this.skills=skills;
            this.skillsChange=skillsChange;
            this.type=trainingType;
        }
    }

    @Data
    public static class TrainingDay {
        private int season;
        private int week;
        private int seasonWeek;
        private int day;
        private TrainingDate date;

        public TrainingDay(int season, int week, int seasonWeek, int day)
        {
            this.season=season;this.week=week;this.seasonWeek=seasonWeek;this.day=day;
        }
    }

    @Data
    public static class TrainingDate {
        private String value;
        private long timestamp;
    }



    @Data
    public static class TrainingType {
        private int code;
        private String name;
        public TrainingType(int cooe, String name)
        {
            this.code=cooe;this.name=name;
        }
    }

    @Data
    public static class TrainingKind {
        private int code;
        private String name;
    }

    @Data
    public static class PlayerValue {
        private long value;
        private String currency;
    }

    @Data
    public static class Games {
        private int minutesOfficial;
        private int minutesFriendly;
        private int minutesNational;
    }

    @Data
    public static class Formation {
        private int code;
        private String name;
    }

    @Data
    public static class Injury {
        private int daysRemaining;
        private boolean severe;
    }
}