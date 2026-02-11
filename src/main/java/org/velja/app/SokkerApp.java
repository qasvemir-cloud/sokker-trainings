    package org.velja.app;

    import lombok.SneakyThrows;
    import org.velja.app.api.ApiClient;
    import org.velja.app.model.PlayerElement;
    import org.velja.app.model.Skills;
    import org.velja.app.model.Team;
    import org.velja.app.model.TrainingReport;
    import javax.swing.*;
    import java.awt.*;
    import java.io.IOException;
    import java.util.LinkedHashMap;
    import java.util.List;
    import java.util.Map;
    import java.util.Objects;
    import java.util.stream.Collectors;

    import static org.velja.app.SkillLabelHelper.addSkillLabel;
    import static org.velja.app.SkillLabelHelper.styleButton;

    public class SokkerApp {
        public static void main(String[] args)
        {
            SwingUtilities.invokeLater(LoginScreen::new);

        }
    }

    class LoginScreen extends JFrame {
        private final JTextField usernameField;
        private final JPasswordField passwordField;
        private final JLabel messageLabel;

        public LoginScreen() {
            setTitle("Sokker Login");
            setSize(400, 300);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(10, 10, 10, 10);

            JLabel titleLabel = new JLabel("Logovanje na Sokker Manager");
            titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            add(titleLabel, gbc);

            gbc.gridwidth = 1;
            gbc.gridy = 1;
            add(new JLabel("Korisničko ime:"), gbc);

            gbc.gridx = 1;
            usernameField = new JTextField("veljizao",15);
            add(usernameField, gbc);

            gbc.gridx = 0;
            gbc.gridy = 2;
            add(new JLabel("Lozinka:"), gbc);

            gbc.gridx = 1;
            passwordField = new JPasswordField("stojke",15);
            add(passwordField, gbc);

            JButton loginButton;
            loginButton = new JButton("Login");
            styleButton(loginButton);
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            add(loginButton, gbc);

            messageLabel = new JLabel("", SwingConstants.CENTER);
            gbc.gridx = 0;
            gbc.gridy = 4;
            add(messageLabel, gbc);

            loginButton.addActionListener(e -> {
                String username = usernameField.getText();
                String password = new String(passwordField.getPassword());
                String success = ApiClient.login(username, password);

                if (success!=null) {
                    dispose();
                    new TeamScreen(Objects.requireNonNull(ApiClient.fetchCurrentTeam(success)));
                    ApiClient.phpSessionId=success;
                } else {
                    messageLabel.setText("Login failed. Try again.");
                }
            });

            setVisible(true);
        }
    }

    class TeamScreen extends JFrame {

        public TeamScreen(Team team) {

            setTitle("Tim");
            setSize(500, 400);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());

            JLabel titleLabel = new JLabel("Informacije o timu", JLabel.CENTER);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
            add(titleLabel, BorderLayout.NORTH);

            JPanel buttonPanel = new JPanel();

            JButton showPlayersButton = new JButton("Prikaži igrače");
            styleButton(showPlayersButton);
            showPlayersButton.addActionListener(e -> {
                dispose();
                new PlayerListScreen(team);
            });

            JButton backButton = new JButton("Nazad");
            styleButton(backButton);
            backButton.addActionListener(e -> {
                dispose();
                new LoginScreen();
            });

            buttonPanel.add(showPlayersButton);
            buttonPanel.add(backButton);

            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(5, 1, 10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            Font labelFont = new Font("Arial", Font.BOLD, 16);

            JLabel nameLabel = new JLabel("Tim: " + team.getName());
            nameLabel.setFont(labelFont);
            panel.add(nameLabel);
            JLabel rankLabel = new JLabel("Rang: " + team.getRank());
            rankLabel.setFont(labelFont);
            panel.add(rankLabel);
            JLabel countryLabel = new JLabel("Država: " + team.getCountry());
            countryLabel.setFont(labelFont);
            panel.add(countryLabel);

            add(buttonPanel, BorderLayout.AFTER_LAST_LINE);
            add(panel, BorderLayout.CENTER);
            setVisible(true);
        }
    }

    class PlayerListScreen extends JFrame {
        public PlayerListScreen(Team team) {
            setTitle("Igrači");
            setSize(500, 400);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout());

            JLabel titleLabel = new JLabel("Informacije o igračima", JLabel.CENTER);
            titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
            add(titleLabel, BorderLayout.NORTH);

            JButton backButton = new JButton("NAZAD");
            styleButton(backButton);
            backButton.setForeground(Color.BLACK);
            backButton.addActionListener(e -> {
                dispose();
                new TeamScreen(Objects.requireNonNull(ApiClient.fetchCurrentTeam(ApiClient.phpSessionId)));
            });
            add(backButton, BorderLayout.SOUTH);

            JPanel panel = new JPanel();
            panel.setLayout(new GridLayout(0,1,10,10));
            List<PlayerElement> players = ApiClient.fetchPlayers(team.getId());

            for (PlayerElement player : players) {
                JButton playerButton = new JButton(player.getInfo().getName().getFull() + " (" + player.getInfo().getCharacteristics().getAge() + " years)");
                styleButton(playerButton);
                playerButton.addActionListener(e -> {
                    dispose();
                    new PlayerDetailScreen(player, team);
                });
                panel.add(playerButton);
            }

            add(new JScrollPane(panel), BorderLayout.CENTER);
            setVisible(true);
        }
    }

    class PlayerDetailScreen extends JFrame {
        public PlayerDetailScreen(PlayerElement player, Team team)
        {
            setTitle("Detalji o igraču");
            setSize(500, 400);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton trainingButton = new JButton("Prikaži trening za igrača");
            styleButton(trainingButton);
            trainingButton.addActionListener(e -> {
                dispose();
                openTrainingReport(player, team);
            });
            JButton trainingSumButton = new JButton("Prikaži total treninga za igrača");
            styleButton(trainingSumButton);
            trainingSumButton.addActionListener(e -> {
                dispose();
                openTrainingSumReport(player, team);
            });
            JButton backButton = new JButton("Nazad");
            styleButton(backButton);
            backButton.addActionListener(e -> {
                dispose();
                new PlayerListScreen(team);
            });
            buttonPanel.add(trainingButton);
            buttonPanel.add(trainingSumButton);
            buttonPanel.add(backButton);
            add(buttonPanel, BorderLayout.NORTH);

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.gridx = 0;
            gbc.gridy = 0;

            Font labelFont = new Font("Arial", Font.BOLD, 16);
            JLabel iDLabel = new JLabel("ID: " + player.getId());
            iDLabel.setFont(labelFont);
            panel.add(iDLabel,gbc);
            gbc.gridy++;
            JLabel nameLabel = new JLabel("Name: " + player.getInfo().getName().getFull());
            nameLabel.setFont(labelFont);
            panel.add(nameLabel,gbc);
            gbc.gridy++;
            JLabel ageLabel = new JLabel("Age: " + player.getInfo().getCharacteristics().getAge());
            ageLabel.setFont(labelFont);
            panel.add(ageLabel,gbc);
            gbc.gridy++;

            JPanel skillPanel = new JPanel(new GridLayout(5, 2, 10, 10));

            Map<String, Integer> skillValues = new LinkedHashMap<>();
            skillValues.put("form", player.getInfo().getSkills().getForm());
            skillValues.put("tacticalDiscipline", player.getInfo().getSkills().getTacticalDiscipline());
            skillValues.put("stamina", player.getInfo().getSkills().getStamina());
            skillValues.put("keeper", player.getInfo().getSkills().getKeeper());
            skillValues.put("pace", player.getInfo().getSkills().getPace());
            skillValues.put("defender", player.getInfo().getSkills().getDefending());
            skillValues.put("technique", player.getInfo().getSkills().getTechnique());
            skillValues.put("playmaker", player.getInfo().getSkills().getPlaymaking());
            skillValues.put("passing", player.getInfo().getSkills().getPassing());
            skillValues.put("striker", player.getInfo().getSkills().getStriker());

            for (Map.Entry<String, Integer> entry : skillValues.entrySet()) {
                addSkillLabel( entry.getKey(),entry.getValue(),0,skillPanel);
            }

            gbc.gridwidth = 2;
            panel.add(skillPanel, gbc);

            add(panel);
            pack(); // Automatski resize prozora ako je potrebno
            setVisible(true);
        }
        @SneakyThrows
        private void openTrainingReport(PlayerElement player, Team team)
        {
            List<TrainingReport.TrainingEntry> reports ;

            try {
                if(true) {
                    reports = ApiClient.getTrainingReports(player);
                }
                else
                {
                   reports = ApiClient.getTrainingReportsNonAdmin(player);
                }
                new TrainingReportScreen(reports, player, team);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Greška prilikom učitavanja trening izveštaja.");
            }
        }
        @SneakyThrows
        private void openTrainingSumReport(PlayerElement player, Team team)
        {
            List<TrainingReport.TrainingEntry> reports;
            try {
                reports = ApiClient.getTrainingReports(player);
                SkillPredictor.predictSkills(reports, player.getInfo().getCharacteristics().getAge());
                new TrainingReportSumScreen(reports, player, team);
            }
            catch (IOException ex)
            {
                JOptionPane.showMessageDialog(this, "Greška prilikom učitavanja trening izveštaja.");
            }
        }

    }

    class TrainingReportScreen extends JFrame {
        public TrainingReportScreen(List<TrainingReport.TrainingEntry> reports, PlayerElement player, Team team) {
            setTitle("Trening izveštaji");
            setSize(400, 300);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JButton backButton = new JButton("Nazad");
            styleButton(backButton);
            backButton.addActionListener(e -> {
                dispose();
                new PlayerDetailScreen(player, team);
            });
            add(backButton, BorderLayout.NORTH);

            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            for (TrainingReport.TrainingEntry trainingEntry : reports) {
                JButton reportButton = new JButton("Trening izvestaj - Sezona " + trainingEntry.getDay().getSeason() + ", Sedmica " + trainingEntry.getDay().getSeasonWeek());
                styleButton(reportButton);
                reportButton.addActionListener(e -> {
                    dispose();
                    new TrainingDetailScreen(trainingEntry, player, team, reports);
                });
                panel.add(reportButton);
            }
            add(new JScrollPane(panel));
            setVisible(true);
        }
    }

    class TrainingReportSumScreen extends JFrame {
        public TrainingReportSumScreen(List<TrainingReport.TrainingEntry> reports, PlayerElement player, Team team) {
            setTitle("Total");
            setSize(400, 300);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.gridx = 0;
            gbc.gridy = 0;

            Font labelFont = new Font("Arial", Font.BOLD, 16);
            JLabel iDLabel = new JLabel("ID: " + player.getId());
            iDLabel.setFont(labelFont);
            panel.add(iDLabel,gbc);
            gbc.gridy++;
            JLabel nameLabel = new JLabel("Name: " + player.getInfo().getName().getFull());
            nameLabel.setFont(labelFont);
            panel.add(nameLabel,gbc);
            gbc.gridy++;
            JLabel ageLabel = new JLabel("Age: " + player.getInfo().getCharacteristics().getAge());
            ageLabel.setFont(labelFont);
            panel.add(ageLabel,gbc);
            gbc.gridy++;

            // SkillPanel sa dve kolone za prikaz skillova
            JPanel skillPanel = new JPanel(new GridLayout(6, 2, 10, 10)); // Ostaje sa GridLayout za skillove

            Skills skills = reports.get(reports.size()-1).getSkills();
            // Using the utility class to add skill labels
            addSkillLabel("Stamina", player.getInfo().getSkills().getStamina(),
                    player.getInfo().getSkills().getStamina() - skills.getStamina(), skillPanel);
            addSkillLabel("Keeper", player.getInfo().getSkills().getKeeper(),
                    player.getInfo().getSkills().getKeeper() - skills.getKeeper(), skillPanel);
            addSkillLabel("Pace", player.getInfo().getSkills().getPace(),
                    player.getInfo().getSkills().getPace() - skills.getPace(), skillPanel);
            addSkillLabel("Defending", player.getInfo().getSkills().getDefending(),
                    player.getInfo().getSkills().getDefending() - skills.getDefending(), skillPanel);
            addSkillLabel("Technique", player.getInfo().getSkills().getTechnique(),
                    player.getInfo().getSkills().getTechnique() - skills.getTechnique(), skillPanel);
            addSkillLabel("Playmaking", player.getInfo().getSkills().getPlaymaking(),
                    player.getInfo().getSkills().getPlaymaking() - skills.getPlaymaking(), skillPanel);
            addSkillLabel("Passing", player.getInfo().getSkills().getPassing(),
                    player.getInfo().getSkills().getPassing() - skills.getPassing(), skillPanel);
            addSkillLabel("Striker", player.getInfo().getSkills().getStriker(),
                    player.getInfo().getSkills().getStriker() - skills.getStriker(), skillPanel);

            // Dodajemo skillPanel u glavni panel
            gbc.gridwidth = 2;
            panel.add(skillPanel, gbc);

            // Novi panel za grupisanje rezultata po tipu treninga
            JPanel resultPanel = new JPanel();
            resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.Y_AXIS)); // Vertikalni raspored
            // Dodajemo naslov za grupisane rezultate
            resultPanel.add(new JLabel("Ukupno treninga po skilovima:"+reports.size()));

            // Grupisanje po "name" iz polja "type" i brojanje
            Map<String, Long> groupedByType = reports.stream()
                    .collect(Collectors.groupingBy(entry -> entry.getType().getName(), Collectors.counting()));

            // Ispis rezultata u novim redovima
            groupedByType.forEach((name, count) -> {
                JLabel label = new JLabel(name + ": " + count + " ");
                resultPanel.add(label);
            });

            // Dodajemo rezultatPanel u glavni panel ispod skillPanel
            gbc.gridy++;
            panel.add(resultPanel, gbc);

            add(panel);

            JButton backButton = new JButton("NAZAD");
            styleButton(backButton);
            backButton.addActionListener(e -> {
                dispose();
                new PlayerDetailScreen(player, team);
            });
            add(backButton, BorderLayout.NORTH);

            pack();
            setVisible(true);
        }

        // Reusing the SkillLabelHelper method
        private void addSkillLabel(String name, double value, double change, JPanel skillPanel) {
            SkillLabelHelper.addSkillLabel(name, value, change, skillPanel);
        }
    }

    class TrainingDetailScreen extends JFrame {
        public TrainingDetailScreen(TrainingReport.TrainingEntry report, PlayerElement player, Team team, List<TrainingReport.TrainingEntry> reports) {
            setTitle("Detalji treninga za igrača");
            setSize(400, 300);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.gridx = 0;
            gbc.gridy = 0;

            Font labelFont = new Font("Arial", Font.BOLD, 16);
            JLabel iDLabel = new JLabel("ID: " + player.getId());
            iDLabel.setFont(labelFont);
            panel.add(iDLabel,gbc);
            gbc.gridy++;
            JLabel nameLabel = new JLabel("Name: " + player.getInfo().getName().getFull());
            nameLabel.setFont(labelFont);
            panel.add(nameLabel,gbc);
            gbc.gridy++;
            JLabel ageLabel = new JLabel("Age: " + player.getInfo().getCharacteristics().getAge());
            ageLabel.setFont(labelFont);
            panel.add(ageLabel,gbc);
            gbc.gridy++;

            JPanel skillPanel = new JPanel(new GridLayout(5, 2, 10, 10));

            // Reusing SkillLabelHelper to add skill details
            addSkillLabel("Form", report.getSkills().getForm(), report.getSkillsChange().getForm(), skillPanel);
            addSkillLabel("Tactical discipline", report.getSkills().getTacticalDiscipline(), report.getSkillsChange().getTacticalDiscipline(), skillPanel);
            addSkillLabel("Stamina", report.getSkills().getStamina(), report.getSkillsChange().getStamina(), skillPanel);
            addSkillLabel("Keeper", report.getSkills().getKeeper(), report.getSkillsChange().getKeeper(), skillPanel);
            addSkillLabel("Pace", report.getSkills().getPace(), report.getSkillsChange().getPace(), skillPanel);
            addSkillLabel("Defending", report.getSkills().getDefending(), report.getSkillsChange().getDefending(), skillPanel);
            addSkillLabel("Technique", report.getSkills().getTechnique(), report.getSkillsChange().getTechnique(), skillPanel);
            addSkillLabel("Playmaking", report.getSkills().getPlaymaking(), report.getSkillsChange().getPlaymaking(), skillPanel);
            addSkillLabel("Passing", report.getSkills().getPassing(), report.getSkillsChange().getPassing(), skillPanel);
            addSkillLabel("Striker", report.getSkills().getStriker(), report.getSkillsChange().getStriker(), skillPanel);

            gbc.gridwidth = 2;
            panel.add(skillPanel, gbc);

            add(panel);

            JButton backButton = new JButton("NAZAD");
            styleButton(backButton);
            backButton.addActionListener(e -> {
                dispose();
                new TrainingReportScreen(reports, player, team);
            });
            add(backButton, BorderLayout.NORTH);

            pack();
            setVisible(true);
        }

        // Reusing SkillLabelHelper to add skill details
        private void addSkillLabel(String name, double value, double change, JPanel skillPanel) {
            SkillLabelHelper.addSkillLabel(name, value, change, skillPanel);
        }
    }