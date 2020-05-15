package eu.izmoqwy.parkourchallenge;

import eu.izmoqwy.parkourchallenge.commands.ParkourCommand;
import eu.izmoqwy.parkourchallenge.database.DatabaseQueries;
import eu.izmoqwy.parkourchallenge.database.DatabaseSerializer;
import eu.izmoqwy.parkourchallenge.database.RemoteDatabase;
import eu.izmoqwy.parkourchallenge.listeners.EnhancementsListener;
import eu.izmoqwy.parkourchallenge.listeners.ParkourListener;
import eu.izmoqwy.parkourchallenge.model.Parkour;
import eu.izmoqwy.parkourchallenge.model.ParkourPlayer;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.logging.Level;

@Getter
public final class ParkourChallenge extends JavaPlugin {

    private ParkourListener parkourListener;
    private ParkourCommand parkourCommand;

    private RemoteDatabase database;
    private DatabaseQueries queries;
    private BukkitRunnable autoCommitTask;

    private List<Parkour> parkourList;
    private Map<UUID, ParkourPlayer> parkourPlayers;

    private PlayersTask playersTask;
    private ConcurrentMap<Player, Parkour> jumpingPlayers;

    @Override
    public void onEnable() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        }
        catch (ClassNotFoundException ignored) {
            getLogger().severe("This plugin is a MySQL-driven project, your server needs the MySQL's JDBC driver to run it.");
            setEnabled(false);
            return;
        }

        // personal use for debugging (when I can't use hot reload)
        boolean developmentCtx = getServer().spigot().getConfig().getString("running-environment", "production").toLowerCase().equals("development");
        getLogger().setLevel(developmentCtx ? Level.ALL : Level.INFO);

        // not using saveDefaultConfig because we want to force the default keys to be defined
        getConfig().options().copyDefaults(true);
        saveConfig();

        jumpingPlayers = new ConcurrentHashMap<>();
        playersTask = new PlayersTask(this);

        ConfigurationSection databaseConfiguration = getConfig().getConfigurationSection("database");
        ConfigurationSection mysqlConfiguration = databaseConfiguration.getConfigurationSection("mysql");
        database = new RemoteDatabase(this,
                mysqlConfiguration.getString("host"), mysqlConfiguration.getInt("port"), mysqlConfiguration.getString("database"));

        Listener tempListener;
        Bukkit.getPluginManager().registerEvents(tempListener = new Listener() {
            @EventHandler
            public void onPlayerLogin(PlayerLoginEvent event) {
                event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
                event.setKickMessage(ChatColor.RED + "The database is still loading.");
            }
        }, this);

        // not registering ParkourListener here but it must be defined for the plugin to load the parkours
        parkourListener = new ParkourListener(this);
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long startAt = System.currentTimeMillis();
                    database.connect(mysqlConfiguration.getString("username"), mysqlConfiguration.getString("password"));
                    getLogger().info("Connected to database.");
                    String tablesPrefix = databaseConfiguration.getString("tables-prefix");

                    /*
                    Why VARCHAR(85) for locations?

                    world uuid -> 36 chars
                    x and y are limited to 30 millions and can be negative so a length of 1+8 and is rounded to 2 digits after a . -> (1+8+1+2) * 2 = 24 chars
                    y is limited to 256 and some digits -> 3+1+2 = 6 chars
                    yaw and pitch are limited to 180 with some digits and can be negative -> (1+3+1+2) * 2 = 14
                    these 6 values are separated by 5 semicolons -> 5 chars

                    Total -> 36 + 24 + 6 + 14 + 5 = 85 chars
                    */
                    String parkoursTableCreationQuery = "CREATE TABLE IF NOT EXISTS " + tablesPrefix + "parkours ("
                            + "	id INTEGER PRIMARY KEY AUTO_INCREMENT,"
                            + " name VARCHAR(18) UNIQUE NOT NULL,"
                            + " spawnLocation VARCHAR(85) NOT NULL,"
                            + " startLocation VARCHAR(85) NOT NULL,"
                            + " endLocation VARCHAR(85) NOT NULL,"
                            + "	experience INTEGER(5) NOT NULL"
                            + ");";
                    String attemptsTableCreationQuery = "CREATE TABLE IF NOT EXISTS " + tablesPrefix + "attempts ("
                            + " parkour_id INTEGER NOT NULL,"
                            + " player VARCHAR(36) NOT NULL,"
                            + " time INTEGER(9) NOT NULL"
                            + ");";
                    String playersTableCreationQuery = "CREATE TABLE IF NOT EXISTS " + tablesPrefix + "players ("
                            + " uuid VARCHAR(36) NOT NULL,"
                            + " experience INTEGER(11) NOT NULL"
                            + ");";

                    Connection connection = database.getConnection();
                    for (String query : Arrays.asList(parkoursTableCreationQuery, attemptsTableCreationQuery, playersTableCreationQuery)) {
                        connection.prepareStatement(query).execute();
                    }
                    database.getConnection().commit();
                    queries = new DatabaseQueries(tablesPrefix);

                    parkourList = new ArrayList<>();
                    String parkoursSelectQuery = "SELECT * FROM " + tablesPrefix + "parkours\n" +
                            " WHERE 1;";
                    PreparedStatement preparedStatement = database.getConnection().prepareStatement(parkoursSelectQuery);
                    ResultSet resultSet = preparedStatement.executeQuery();
                    while (resultSet.next()) {
                        addParkour(new Parkour(
                                resultSet.getInt("id"),
                                resultSet.getString("name"),
                                DatabaseSerializer.stringToLocation(resultSet.getString("spawnLocation")),
                                DatabaseSerializer.stringToLocation(resultSet.getString("startLocation")),
                                DatabaseSerializer.stringToLocation(resultSet.getString("endLocation")),
                                resultSet.getInt("experience")
                        ));
                    }
                    preparedStatement.close();
                    getLogger().info(parkourList.size() + " parkour maps have been loaded.");
                    getLogger().info("The database took " + (System.currentTimeMillis() - startAt) + "ms to initialize.");
                    sync(() -> {
                        HandlerList.unregisterAll(tempListener);
                        postDbSetup(developmentCtx);
                    });
                }
                catch (SQLException e) {
                    e.printStackTrace();
                    getLogger().severe("Unable to establish connection to the database.");
                    sync(() -> setEnabled(false));
                }
            }
        }.runTaskAsynchronously(this);
    }

    private void postDbSetup(boolean developmentCtx) {
        parkourPlayers = new HashMap<>();

        autoCommitTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    database.getConnection().commit();
                }
                catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        };
        // commit attempts and players every 5 minutes
        autoCommitTask.runTaskTimerAsynchronously(this, 10 * 60 * 20, 5 * 60 * 20);

        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(parkourListener.postDbLoad(), this);
        pluginManager.registerEvents(new EnhancementsListener(), this);

        getCommand("parkour").setExecutor(parkourCommand = new ParkourCommand(this));

        // 1 second = 20 ticks -> 100 ms = 2 ticks
        playersTask.runTaskTimer(this, 2, 2);

        if (developmentCtx) {
            Bukkit.getOnlinePlayers().forEach(player -> {
                PlayerJoinEvent fakePlayerJoinEvent = new PlayerJoinEvent(player, null);
                parkourListener.onJoin(fakePlayerJoinEvent);
            });
            getLogger().info("Faked " + Bukkit.getOnlinePlayers().size() + " joins for development purposes.");
        }
    }

    @Override
    public void onDisable() {
        if (database.getConnection() == null)
            return;
        try {
            if (autoCommitTask != null)
                autoCommitTask.cancel();
            // commit in main thread (most of the time nothing needs to be committed here)
            database.getConnection().commit();
            database.disconnect();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addParkour(Parkour parkour) {
        int alreadyExistingIndex = parkourList.indexOf(parkour);
        if (alreadyExistingIndex == -1) {
            parkourList.add(parkour);
        }
        else {
            parkourList.get(alreadyExistingIndex).update(parkour);
        }

        Location startLocation = parkour.getStartLocation();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    parkourListener.getStartingLocations().put(toFloorVector(startLocation, x, y, z), parkour.getDatabaseId());
                }
            }
        }
    }

    // this method saves 1 Vector object instantiation
    private Vector toFloorVector(Location location, int addX, int addY, int addZ) {
        return new Vector(location.getBlockX() + addX, location.getBlockY() + addY, location.getBlockZ() + addZ);
    }

    /*
    Join/leave a parkour
    db operations: log attempts, load leaderboards & update player's XP
     */

    public void joinParkour(Player player, Parkour parkour) {
        if (player == null || parkour == null)
            return;

        if (parkourCommand.getParkourBuilders().containsKey(player)) {
            player.sendMessage(ChatColor.RED + "You can't join a parkour map when you are editing/creating one.");
            return;
        }

        jumpingPlayers.put(player, parkour);
        player.setAllowFlight(false);
        player.teleport(parkour.getSpawnLocation());
        player.setExp(0);
        player.setLevel(0);
        // using setCustomName prevent the creation of another map or having a wrapping object with Parkour and Timestamp
        // since the plugin is meant to be the main game mode of the server it's safe to use
        player.setCustomName(String.valueOf(System.currentTimeMillis()));
        player.sendMessage(ChatColor.GREEN + "Let's start jumping!");
        parkourListener.updatePlayerStatus(player, true);

        if (parkour.getLeaderboard() == null) {
            if (parkour.getDatabaseId() == 0)
                return;

            try {
                PreparedStatement preparedStatement = database.getConnection().prepareStatement(queries.getLeaderboardSelectQuery());
                preparedStatement.setInt(1, parkour.getDatabaseId());

                // I prefer using a HashMap and sorting on read rather than using a TreeMap when comparing by value
                Map<OfflinePlayer, Integer> bestScores = new HashMap<>();
                asyncSelect(preparedStatement,
                        resultSet -> {
                            try {
                                while (resultSet.next()) {
                                    String uuid = resultSet.getString("player");
                                    // when UUID != 36 -> manual insertion for testing
                                    bestScores.put(Bukkit.getOfflinePlayer(uuid.length() != 36 ? UUID.randomUUID() : UUID.fromString(uuid)), resultSet.getInt("time"));
                                }
                            }
                            catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }, success -> sync(() -> {
                            parkour.setLeaderboard(new ParkourLeaderboard(parkour, bestScores));
                            refreshLeaderboard(parkour.getLeaderboard());
                        }));
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
        }
        else {
            player.setScoreboard(parkour.getLeaderboard().getScoreboard());
        }
    }

    public void endParkour(Player player, boolean abandon) {
        if (player == null)
            return;

        Parkour parkour = jumpingPlayers.remove(player);
        if (parkour == null)
            return;

        if (player.getScoreboard() == parkour.getLeaderboard().getScoreboard())
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        int time = (int) (System.currentTimeMillis() - Long.parseLong(player.getCustomName()));
        player.setAllowFlight(true);
        player.setExp(0);
        player.setLevel(1);
        player.giveExp(parkour.getExperience());
        if (parkourPlayers.containsKey(player.getUniqueId())) {
            ParkourPlayer parkourPlayer = parkourPlayers.get(player.getUniqueId());
            player.giveExp(parkourPlayer.getExperience());
            parkourPlayer.setExperience(parkourPlayer.getExperience() + parkour.getExperience());
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        PreparedStatement preparedStatement = database.getConnection().prepareStatement(queries.getPlayerUpdateQuery());
                        preparedStatement.setInt(1, parkourPlayer.getExperience());
                        preparedStatement.setString(2, player.getUniqueId().toString());
                        preparedStatement.execute();
                        // let auto commit task commit the update
                    }
                    catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }.runTaskAsynchronously(this);
        }

        if (abandon) {
            player.sendMessage(ChatColor.DARK_GREEN + "We didn't save your attempt.");
        }
        else {
            player.sendMessage(ChatColor.GREEN + "Congrats! You finished '" +
                    ChatColor.translateAlternateColorCodes('&', parkour.getName()) + ChatColor.GREEN + "' in " + TimeFormatter.fromMillis(time) + "!");

            ParkourLeaderboard leaderboard = parkour.getLeaderboard();
            if (leaderboard != null) {
                if (leaderboard.getBestScores().size() < 10) {
                    if (leaderboard.getBestScores().containsKey(player)) {
                        if (leaderboard.getBestScores().get(player) > time) {
                            leaderboard.getBestScores().put(player, time);
                            leaderboard.refresh();
                        }
                    }
                    else {
                        leaderboard.getBestScores().put(player, time);
                        leaderboard.refresh();
                    }
                }
                else {
                    Map.Entry<OfflinePlayer, Integer> worstScore = Collections.max(leaderboard.getBestScores().entrySet(), Comparator.comparingInt(Map.Entry::getValue));
                    if (worstScore.getValue() > time) {
                        leaderboard.getBestScores().remove(worstScore.getKey());
                        leaderboard.getBestScores().put(player, time);
                        leaderboard.refresh();
                    }
                }
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        PreparedStatement preparedStatement = database.getConnection().prepareStatement(queries.getAttemptInsertQuery());
                        preparedStatement.setInt(1, parkour.getDatabaseId());
                        preparedStatement.setString(2, player.getUniqueId().toString());
                        preparedStatement.setInt(3, time);
                        preparedStatement.execute();
                        // let auto commit task commit the attempt
                    }
                    catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }.runTaskAsynchronously(this);
        }
        parkourListener.updatePlayerStatus(player, false);
    }

    public void refreshLeaderboard(ParkourLeaderboard leaderboard) {
        leaderboard.refresh();
        jumpingPlayers.entrySet().stream()
                .filter(entry -> entry.getValue().equals(leaderboard.getParkour()))
                .map(Map.Entry::getKey)
                .forEach(player -> player.setScoreboard(leaderboard.getScoreboard()));
    }

    /*
    Sync/async utils
     */

    public void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(this, runnable);
    }

    public void asyncSelect(PreparedStatement preparedStatement, Consumer<ResultSet> resultSetConsumer, Consumer<Boolean> callback) {
        database.asyncSelect(this, preparedStatement, resultSetConsumer, callback);
    }

}
