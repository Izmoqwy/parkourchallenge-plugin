package eu.izmoqwy.parkourchallenge.database;

import lombok.Getter;

/*
Queries used across the plugin expect the ones when the plugin loads
 */

@Getter
public class DatabaseQueries {

    private final String parkourInsertQuery, parkourUpdateQuery, parkourGetIdQuery;
    private final String playerInsertQuery, playerUpdateQuery, playerSelectQuery;
    private final String attemptInsertQuery, leaderboardSelectQuery;

    public DatabaseQueries(String tablesPrefix) {
        this.parkourInsertQuery = "INSERT INTO " + tablesPrefix + "parkours (name, spawnLocation, startLocation, endLocation, experience)\n" +
                " VALUES (?, ?, ?, ?, ?);";
        this.parkourUpdateQuery = "UPDATE " + tablesPrefix + "parkours\n" +
                " SET name = ?, spawnLocation = ?, startLocation = ?, endLocation = ?, experience = ?\n" +
                " WHERE id = ?;";
        this.parkourGetIdQuery = "SELECT id FROM " + tablesPrefix + "parkours WHERE name = ?;";

        this.playerInsertQuery = "INSERT INTO " + tablesPrefix + "players (uuid, experience)\n" +
                " VALUES (?, ?);";
        this.playerUpdateQuery = "UPDATE " + tablesPrefix + "players\n" +
                " SET experience = ?\n" +
                " WHERE uuid = ?;";
        this.playerSelectQuery = "SELECT * FROM " + tablesPrefix + "players\n" +
                " WHERE uuid = ?;";

        this.attemptInsertQuery = "INSERT INTO " + tablesPrefix + "attempts (parkour_id, player, time)\n" +
                " VALUES (?, ?, ?);";
         /*
             using an inner limit to fix an issue related to MariaDB:
             https://mariadb.com/kb/en/why-is-order-by-in-a-from-subquery-ignored/

             (
                Full query: SELECT player, time FROM ( SELECT * FROM attempts ORDER BY time ASC LIMIT 100000 ) AS sub GROUP BY sub.player ORDER BY sub.time LIMIT 10;
                in this case LIMIT 100000 is WAY enough because it will require each player from the top 10 to have 10000 attempts in the leaderboard to break it
                in other words, this "fake limit" will NEVER be a problem, even with thousands of people playing and millions of attempts
             )
             */
        this.leaderboardSelectQuery = "SELECT player, time FROM\n" +
                "( SELECT * FROM " + tablesPrefix + "attempts\n" +
                "  WHERE parkour_id = ?\n" +
                "  ORDER BY time ASC\n" +
                "  LIMIT 100000\n" +
                ") AS sub\n" +
                " GROUP BY sub.player\n" +
                " ORDER BY sub.time\n" +
                " LIMIT 10;";
    }

}
