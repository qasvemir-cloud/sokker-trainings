package org.velja.app;

import java.util.*;
import org.velja.app.model.*;

public class SkillPredictor {

    public static void predictSkills(List<TrainingReport.TrainingEntry> reports, int playerAge) {
        Map<String, Double> skillValues = new HashMap<>();
        TrainingReport.TrainingEntry firstReport = reports.get(reports.size()-1);

        Map<String, Integer> skillIntegerValues = extractSkills(firstReport.getSkills());
        Map<String, Integer> skillJumps = new HashMap<>();
        Map<String, Double> effectiveTrainingCount = new HashMap<>();

        for (String skill : skillIntegerValues.keySet()) {
            skillValues.put(skill, (double) skillIntegerValues.get(skill));
            skillJumps.put(skill, 0);
            effectiveTrainingCount.put(skill, 0.0);
        }

        for (int i = reports.size() - 2; i >= 0; i--) {
            TrainingReport.TrainingEntry prev = reports.get(i + 1);
            TrainingReport.TrainingEntry current = reports.get(i);
            String trainedSkill = current.getType().getName();

            for (String skill : skillValues.keySet()) {
                int prevValue = extractSkills(prev.getSkills()).get(skill);
                int currentValue = extractSkills(current.getSkills()).get(skill);
                double dtIncrement = getTrainingEffect(playerAge, currentValue, true);
                double gtIncrement = getTrainingEffect(playerAge, currentValue, false);

                if (prevValue < currentValue) {

                    skillJumps.put(skill, skillJumps.get(skill) + 1);
                    skillValues.put(skill, (double) currentValue);
                } else {
                    if (trainedSkill.equals(skill)) {
                        skillValues.put(skill, skillValues.get(skill) + dtIncrement);
                        effectiveTrainingCount.put(skill, effectiveTrainingCount.get(skill) + 1);
                    } else {
                        skillValues.put(skill, skillValues.get(skill) + gtIncrement);
                        effectiveTrainingCount.put(skill, effectiveTrainingCount.get(skill) + (1 / 4.5));
                    }
                }
            }
        }

        System.out.println("Trenutni skilovi u decimalnom formatu:");
        for (String skill : skillValues.keySet()) {
            double estimatedValue = Math.min(skillValues.get(skill), 18.0);
            System.out.printf("%s: %.2f\n", skill, estimatedValue);
        }

        System.out.println("\nProsečan broj nedelja za skok po skilu:");
        Map<String, Double> avgWeeksPerJump = new HashMap<>();
        for (String skill : skillJumps.keySet()) {
            if (skillJumps.get(skill) > 0) {
                double avgWeeks = effectiveTrainingCount.get(skill) / skillJumps.get(skill);
                avgWeeksPerJump.put(skill, avgWeeks);
                System.out.printf("%s: %.2f nedelja po skoku\n", skill, avgWeeks);
            }
        }

        // Predikcija za sledeći trening za sve skilove
        System.out.println("\nVerovatnoća skoka i broj potrebnih DT za svaki skill ako se trenira sledeće nedelje:");
        for (String skill : skillValues.keySet()) {
            double chanceOfIncrease = getChanceOfIncrease(playerAge, skillValues.get(skill));
            double dtRequired = avgWeeksPerJump.getOrDefault(skill, 0.0); // Koristi prosečan broj nedelja
            System.out.printf("%s: %.2f%% verovatnoća skoka, %.2f DT do skoka\n", skill, chanceOfIncrease * 100, dtRequired);
        }
    }

    private static double getTrainingEffect(int age, double skillLevel, boolean isDirect) {
        double baseIncrement = isDirect ? 0.25 : 0.25 / 4.5;
        if (age <= 18 && skillLevel < 10) return baseIncrement;
        if (age <= 21 && skillLevel >= 11) return baseIncrement * 0.9;
        if (age > 21) return baseIncrement * 0.8;
        return baseIncrement;
    }

    private static double getChanceOfIncrease(int age, double skillLevel) {
        double baseChance = 1.0 / (skillLevel * 1.2);
        if (age <= 18) return baseChance;
        if (age <= 21) return baseChance * 0.85;
        return baseChance * 0.7;
    }

    private static Map<String, Integer> extractSkills(Skills skills) {
        Map<String, Integer> skillMap = new HashMap<>();
        skillMap.put("pace", skills.getPace());
        skillMap.put("striker", skills.getStriker());
        skillMap.put("defending", skills.getDefending());
        skillMap.put("technique", skills.getTechnique());
        skillMap.put("passing", skills.getPassing());
        skillMap.put("playmaking", skills.getPlaymaking());
        skillMap.put("keeper", skills.getKeeper());
        return skillMap;
    }
}
