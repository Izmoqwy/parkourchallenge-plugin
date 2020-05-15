package eu.izmoqwy.parkourchallenge;

import eu.izmoqwy.parkourchallenge.model.Parkour;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class ParkourLeaderboard {

    private Parkour parkour;
    private Map<OfflinePlayer, Integer> bestScores;

    private Scoreboard scoreboard;
    private Objective scoreboardObjective;

    private Team[] lines = new Team[10];

    public ParkourLeaderboard(Parkour parkour, Map<OfflinePlayer, Integer> bestScores) {
        this.parkour = parkour;
        this.bestScores = bestScores;

        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.scoreboardObjective = scoreboard.registerNewObjective("leaderboard-" + parkour.getDatabaseId(), "dummy");
        updateName();
        scoreboardObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void updateName() {
        scoreboardObjective.setDisplayName(ChatColor.BLUE + "Top 10 - " + ChatColor.translateAlternateColorCodes('&', parkour.getName()));
    }

    public void refresh() {
        if (bestScores.isEmpty()) {
            setLine(0, ChatColor.GRAY + "Empty leaderboard.");
            setScore(0, 0);
            return;
        }

        int currentLine = 0;
        Map<OfflinePlayer, Integer> sortedBestScores = bestScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        for (Map.Entry<OfflinePlayer, Integer> entry : sortedBestScores.entrySet()) {
            setLine(currentLine, ChatColor.GREEN + entry.getKey().getName() + ChatColor.GRAY + ": " + ChatColor.YELLOW + TimeFormatter.fromMillis(entry.getValue()));
            setScore(currentLine, -currentLine - 1);
            if (++currentLine >= 10) {
                break;
            }
        }
    }

    private void setLine(int line, String value) {
        if (line > lines.length || line < 0)
            return;

        if (value.length() > 16) {
            String initialFirstPart = value.substring(0, 16), initialSecondPart = value.substring(16);
            String firstPart = null, secondPart = null;

            int lastColorCharIndex = initialFirstPart.lastIndexOf('§');
            if (lastColorCharIndex + 1 == initialFirstPart.length()) {
                firstPart = initialFirstPart.substring(0, lastColorCharIndex);
                secondPart = '§' + initialSecondPart;
            }
            else if (lastColorCharIndex != -1) {
                ChatColor lastColor = ChatColor.getByChar(initialFirstPart.charAt(lastColorCharIndex + 1));
                if (lastColor != null) {
                    secondPart = lastColor + initialSecondPart;
                }
            }
            setLine(line, firstPart != null ? firstPart : initialFirstPart, secondPart != null ? secondPart : initialSecondPart);
        }
        else {
            setLine(line, value, null);
        }
    }

    private void setLine(int line, String part1, String part2) {
        if (line > lines.length || line < 0)
            return;

        Team team = getLineTeam(line);
        if (team.getEntries().isEmpty()) {
            String key = ChatColor.BLACK + "" + ChatColor.values()[line] + "" + ChatColor.RESET;
            team.addEntry(key);
            scoreboardObjective.getScore(key).setScore(lines.length - line);
        }

        team.setPrefix(part1 != null ? (part1.length() > 16 ? part1.substring(0, 16) : part1) : "");
        team.setSuffix(part2 != null ? (part2.length() > 16 ? part2.substring(0, 16) : part2) : "");
    }

    private void setScore(int line, int score) {
        scoreboardObjective.getScore(ChatColor.BLACK + "" + ChatColor.values()[line] + "" + ChatColor.RESET).setScore(score);
    }

    private Team getLineTeam(int line) {
        if (lines[line] == null)
            lines[line] = scoreboard.registerNewTeam("text-" + scoreboard.getTeams().size());
        return lines[line];
    }

}
