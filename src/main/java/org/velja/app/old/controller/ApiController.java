package org.velja.app.old.controller;

import org.velja.app.old.model.PlayerElement;
import org.velja.app.old.model.Team;
import org.velja.app.old.model.TrainingReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import org.velja.app.old.service.ApiClientService;

import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private ApiClientService apiClientService;

    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, Model model, HttpSession session) {
        try {
            String phpSessionId = apiClientService.login(username, password);
            System.out.println("Login successful, PHPSESSID: " + phpSessionId);
            session.setAttribute("phpSessionId", phpSessionId);
            Team team = apiClientService.fetchCurrentTeam(phpSessionId);
            if (team != null) {
                model.addAttribute("team", team);
                return "team";
            } else {
                model.addAttribute("error", "Failed to fetch team data.");
                return "login";
            }
        } catch (RuntimeException e) {
            model.addAttribute("error", "Login failed: " + e.getMessage());
            return "login";
        }
    }

    @GetMapping("/team/{teamId}/players")
    public String getPlayers(@PathVariable int teamId, @RequestParam(required = false) String sort, Model model, HttpSession session) {
        try {
            String phpSessionId = (String) session.getAttribute("phpSessionId");
            System.out.println("Fetching players for teamId: " + teamId + ", PHPSESSID: " + phpSessionId);
            if (phpSessionId == null) {
                model.addAttribute("error", "Niste ulogovani. Molimo prijavite se.");
                System.out.println("PHPSESSID is null, redirecting to login");
                return "redirect:/api/login";
            }
            List<PlayerElement> players = apiClientService.fetchPlayers(teamId, phpSessionId);
            System.out.println("Fetched " + players.size() + " players for teamId: " + teamId);
            if (players.isEmpty()) {
                model.addAttribute("error", "Nema dostupnih igrača za ovaj tim.");
            } else {
                if ("byName".equals(sort)) {
                    players.sort(Comparator.comparing(p -> p.getInfo().getName().getFull()));
                } else if ("byAge".equals(sort)) {
                    players.sort(Comparator.comparing(p -> p.getInfo().getCharacteristics().getAge()));
                }
            }
            model.addAttribute("players", players);
            model.addAttribute("teamId", teamId);
            return "players";
        } catch (RuntimeException e) {
            model.addAttribute("error", "Failed to fetch players: " + e.getMessage());
            System.err.println("Error fetching players: " + e.getMessage());
            return "team";
        }
    }

    @GetMapping("/player/{playerId}/details")
    public String getPlayerDetails(@PathVariable int playerId, @RequestParam int teamId, Model model, HttpSession session) {
        try {
            String phpSessionId = (String) session.getAttribute("phpSessionId");
            if (phpSessionId == null) {
                model.addAttribute("error", "Niste ulogovani. Molimo prijavite se.");
                return "redirect:/api/login";
            }
            List<PlayerElement> players = apiClientService.fetchPlayers(teamId, phpSessionId);
            PlayerElement player = players.stream()
                    .filter(p -> p.getId() == playerId)
                    .findFirst()
                    .orElse(null);
            if (player != null) {
                model.addAttribute("player", player);
                model.addAttribute("teamId", teamId);
                return "player-details";
            }
            model.addAttribute("error", "Igrač nije pronađen.");
            return "redirect:/api/team/" + teamId + "/players";
        } catch (RuntimeException e) {
            model.addAttribute("error", "Failed to fetch player details: " + e.getMessage());
            return "redirect:/api/team/" + teamId + "/players";
        }
    }

    @GetMapping("/player/{playerId}/training")
    public String getTrainingReports(@PathVariable int playerId, @RequestParam int teamId, Model model, HttpSession session) {
        try {
            String phpSessionId = (String) session.getAttribute("phpSessionId");
            if (phpSessionId == null) {
                model.addAttribute("error", "Niste ulogovani. Molimo prijavite se.");
                return "redirect:/api/login";
            }
            List<PlayerElement> players = apiClientService.fetchPlayers(teamId, phpSessionId);
            PlayerElement player = players.stream()
                    .filter(p -> p.getId() == playerId)
                    .findFirst()
                    .orElse(null);
            if (player != null) {
                List<TrainingReport.TrainingEntry> reports = apiClientService.getTrainingReports(player, phpSessionId);
                     //   : apiClientService.getTrainingReportsNonAdmin(player, phpSessionId);
                model.addAttribute("reports", reports);
                model.addAttribute("player", player);
                model.addAttribute("teamId", teamId);
                //model.addAttribute("isPlus", isPlus);
                return "training-reports";
            }
            model.addAttribute("error", "Igrač nije pronađen.");
            return "redirect:/api/team/" + teamId + "/players";
        } catch (RuntimeException e) {
            model.addAttribute("error", "Failed to fetch training reports: " + e.getMessage());
            return "redirect:/api/team/" + teamId + "/players";
        }
    }

    @GetMapping("/player/{playerId}/training-details")
    public String getTrainingDetails(@PathVariable int playerId,
                                     @RequestParam int teamId,
                                     @RequestParam int season,
                                     @RequestParam int seasonWeek,
                                     Model model,
                                     HttpSession session) {
        try {
            String phpSessionId = (String) session.getAttribute("phpSessionId");
            if (phpSessionId == null) {
                model.addAttribute("error", "Niste ulogovani. Molimo prijavite se.");
                return "redirect:/api/login";
            }
            List<PlayerElement> players = apiClientService.fetchPlayers(teamId, phpSessionId);
            PlayerElement player = players.stream()
                    .filter(p -> p.getId() == playerId)
                    .findFirst()
                    .orElse(null);
            if (player != null) {
                List<TrainingReport.TrainingEntry> reports = apiClientService.getTrainingReports(player, phpSessionId);
                TrainingReport.TrainingEntry report = reports.stream()
                        .filter(r -> r.getDay().getSeason() == season && r.getDay().getSeasonWeek() == seasonWeek)
                        .findFirst()
                        .orElse(null);
                if (report != null) {
                    model.addAttribute("report", report);
                    model.addAttribute("player", player);
                    model.addAttribute("teamId", teamId);
                    // Dodajem listu skill imena za Thymeleaf
                    model.addAttribute("skillNames", List.of(
                            "form", "stamina", "pace", "passing", "technique", "defending", "goalkeeping"
                    ));
                    return "training-details";
                }
                model.addAttribute("error", "Trening izveštaj nije pronađen.");
            }
            return "redirect:/api/team/" + teamId + "/players";
        } catch (RuntimeException e) {
            model.addAttribute("error", "Failed to fetch training details: " + e.getMessage());
            return "redirect:/api/player/" + playerId + "/training?teamId=" + teamId;
        }
    }

}