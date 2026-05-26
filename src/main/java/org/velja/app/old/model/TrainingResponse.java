package org.velja.app.old.model;

import lombok.Data;
import java.util.List;

@Data
public class TrainingResponse {
    private List<TrainingReport.TrainingEntry> reports;
}