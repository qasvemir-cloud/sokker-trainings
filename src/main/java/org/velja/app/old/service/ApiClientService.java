package org.velja.app.old.service;

import org.velja.app.old.model.PlayerElement;
import org.velja.app.old.model.Team;
import org.velja.app.old.model.TrainingReport;

import java.util.List;

public interface ApiClientService {
    String login(String username, String password);
    Team fetchCurrentTeam(String phpSessionId);
    List<PlayerElement> fetchPlayers(int teamId, String phpSessionId);
    List<TrainingReport.TrainingEntry> getTrainingReports(PlayerElement player, String phpSessionId);
    List<TrainingReport.TrainingEntry> getTrainingReportsNonAdmin(PlayerElement player, String phpSessionId);
}