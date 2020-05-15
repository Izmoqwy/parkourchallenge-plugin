package eu.izmoqwy.parkourchallenge.commands;

import eu.izmoqwy.parkourchallenge.ParkourChallenge;
import eu.izmoqwy.parkourchallenge.database.DatabaseSerializer;
import eu.izmoqwy.parkourchallenge.gui.ParkourSelectorGUI;
import eu.izmoqwy.parkourchallenge.model.Parkour;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourCommand implements CommandExecutor {

    private final ParkourSelectorGUI parkourSelectorGUI;

    private ParkourChallenge plugin;

    // this map is concurrent because it's used by PlayersTask as well
    @Getter
    private Map<OfflinePlayer, Parkour.ParkourBuilder> parkourBuilders;

    public ParkourCommand(ParkourChallenge plugin) {
        this.plugin = plugin;
        this.parkourBuilders = new ConcurrentHashMap<>();
        this.parkourSelectorGUI = ParkourSelectorGUI.create(plugin, this::selectParkour);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(ChatColor.RED + "Sadly, the console can't jump.");
            return true;
        }

        Player player = (Player) sender;
        if (plugin.getJumpingPlayers().containsKey(player)) {
            player.sendMessage(ChatColor.RED + "You must finish or abandon your current run to create or edit a parkour.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (args.length >= 3 && args[1].equalsIgnoreCase("name")) {
                Parkour.ParkourBuilder parkourBuilder = checkParkourBuilderPre(player);
                if (parkourBuilder == null)
                    return true;

                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                if (name.length() > 18) {
                    player.sendMessage(ChatColor.RED + "The name of your parkour must not exceed 18 characters.");
                    return true;
                }

                if (plugin.getParkourList().stream().anyMatch(parkour -> parkour.getName().equalsIgnoreCase(name))) {
                    player.sendMessage(ChatColor.RED + "Another parkour already use this name!");
                    return true;
                }

                if (!ChatColor.getLastColors(ChatColor.translateAlternateColorCodes('&', name)).isEmpty()) {
                    player.sendMessage(ChatColor.GOLD + "Warning: The parkour's name contains colors.");
                }
                parkourBuilder.name(name);

                player.sendMessage(ChatColor.AQUA + "The name of the parkour has been set to '" + name + "'.");
                checkParkourBuilderPost(player);
            }
            else if (checkArgument(args, 1, "spawn", "start", "stop")) {
                Parkour.ParkourBuilder parkourBuilder = checkParkourBuilderPre(player);
                if (parkourBuilder == null)
                    return true;
                Location location = player.getLocation();

                String arg = args[1].toLowerCase();
                switch (arg) {
                    case "spawn":
                        parkourBuilder.spawnLocation(location);
                        break;
                    case "start":
                        Parkour similarParkour = plugin.getParkourListener().getStartingLocations()
                                .get(plugin.getParkourListener().toFloorVector(player.getLocation()));
                        if (similarParkour != null) {
                            player.sendMessage(ChatColor.RED + "Another parkour starts here!");
                            return true;
                        }
                        parkourBuilder.startLocation(location);
                        break;
                    case "stop":
                        parkourBuilder.endLocation(location);
                        break;
                }
                player.sendMessage(ChatColor.AQUA + "The " + arg + " point of your parkour has been set successfully.");
                checkParkourBuilderPost(player);
            }
            else if (args.length >= 3 && args[1].equalsIgnoreCase("exp")) {
                Parkour.ParkourBuilder parkourBuilder = checkParkourBuilderPre(player);
                if (parkourBuilder == null)
                    return true;

                try {
                    int experience = Integer.parseInt(args[2]);
                    if (String.valueOf(experience).length() > 5 || experience < 0) {
                        player.sendMessage(ChatColor.RED + "This is way too much XP!");
                        return true;
                    }

                    parkourBuilder.experience(experience);
                    player.sendMessage(ChatColor.AQUA + "The parkour will now give " + ChatColor.YELLOW + experience + " XP " + ChatColor.AQUA + ".");
                    checkParkourBuilderPost(player);
                }
                catch (NumberFormatException ignored) {
                    player.sendMessage(ChatColor.RED + "Invalid number!");
                }
            }
            else {
                if (!checkArgument(args, 1, "help", "?")) {
                    player.sendMessage(ChatColor.RED + "Invalid usage, please check the help message below.");
                }

                player.sendMessage(ChatColor.BLUE + "Parkour variables help:");
                player.sendMessage(ChatColor.AQUA + "/parkour set name <name> - The name shown in the selection menu & leaderboard");
                player.sendMessage(ChatColor.AQUA + "/parkour set spawn - The location where the player is teleported when he selects the parkour");
                player.sendMessage(ChatColor.AQUA + "/parkour set start - The location where the player automatically joins the parkour");
                player.sendMessage(ChatColor.AQUA + "/parkour set stop - The location where the parkour ends");
                player.sendMessage(ChatColor.AQUA + "/parkour set exp <number> - The XP players will get by finishing the parkour");
            }
        }
        else if (args.length >= 1 && args[0].equalsIgnoreCase("select")) {
            player.openInventory(parkourSelectorGUI.getInventory());
        }
        else if (args.length >= 1 && args[0].equalsIgnoreCase("save")) {
            Parkour.ParkourBuilder parkourBuilder = parkourBuilders.get(player);
            if (parkourBuilder != null && parkourBuilder.isEditing()) {
                if (parkourBuilder.isReady()) {
                    if (saveParkour(parkourBuilder)) {
                        parkourBuilders.remove(player);
                        player.sendMessage(ChatColor.GREEN + "You successfully edited the parkour '" + parkourBuilder.getName() + "'!");
                    }
                    else {
                        player.sendMessage(ChatColor.DARK_RED + "Unable to save the edits made to your currently selected parkour!");
                    }
                    player.setGameMode(GameMode.ADVENTURE);
                }
                else {
                    player.sendMessage(ChatColor.DARK_RED + "Unreachable code reached!");
                }
            }
            else {
                player.sendMessage(ChatColor.RED + "You are not editing an existing parkour, there is nothing to save.");
            }
        }
        else if (args.length >= 1 && args[0].equalsIgnoreCase("discard")) {
            Parkour.ParkourBuilder parkourBuilder = parkourBuilders.get(player);
            if (parkourBuilder != null) {
                parkourBuilders.remove(player);
                if (parkourBuilder.isEditing())
                    player.sendMessage(ChatColor.DARK_GREEN + "Parkour changes discarded!");
                else player.sendMessage(ChatColor.DARK_GREEN + "Your cancelled the creation of your parkour map.");
                player.setGameMode(GameMode.ADVENTURE);
            }
            else {
                player.sendMessage(ChatColor.RED + "You are not editing or creating a parkour map, there is nothing to discard.");
            }
        }
        else {
            player.sendMessage(ChatColor.BLUE + "Parkour command help:");
            player.sendMessage(ChatColor.AQUA + "/parkour set <name|spawn|start|stop|exp> - Set variables for your currently selected parkour");
            player.sendMessage(ChatColor.AQUA + "/parkour select - Select an already existing parkour to edit it");
            player.sendMessage(ChatColor.AQUA + "/parkour save - Save an already existing parkour after editing it");
            player.sendMessage(ChatColor.AQUA + "/parkour discard - Discard changes instead of saving them");
        }
        return true;
    }

    private Parkour.ParkourBuilder checkParkourBuilderPre(Player player) {
        if (!parkourBuilders.containsKey(player)) {
            if (plugin.getParkourList().size() >= 18) {
                player.sendMessage(ChatColor.RED + "The server has reached his parkour maps limit (18)!");
                return null;
            }
            parkourBuilders.put(player, Parkour.builder());
            player.sendMessage(ChatColor.BLUE + "Your new parkour has been initialized. It will be saved automatically when you set the last variable.");
            player.setGameMode(GameMode.CREATIVE);
        }
        return parkourBuilders.get(player);
    }

    private void checkParkourBuilderPost(Player player) {
        Parkour.ParkourBuilder parkourBuilder = parkourBuilders.get(player);
        if (parkourBuilder == null || parkourBuilder.isEditing())
            return;

        if (parkourBuilder.isReady()) {
            if (saveParkour(parkourBuilder)) {
                parkourBuilders.remove(player);
                player.sendMessage(ChatColor.GREEN + "Your new parkour has been published!");
            }
            else {
                player.sendMessage(ChatColor.DARK_RED + "Failed to publish your parkour!");
            }
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private boolean saveParkour(Parkour.ParkourBuilder parkourBuilder) {
        if (!parkourBuilder.isReady())
            return false;

        try {
            boolean update = parkourBuilder.isEditing();
            Parkour parkour = parkourBuilder.build();
            PreparedStatement preparedStatement = plugin.getDatabase().getConnection().prepareStatement(
                    update ? plugin.getQueries().getParkourUpdateQuery() : plugin.getQueries().getParkourInsertQuery());

            preparedStatement.setString(1, parkour.getName());
            preparedStatement.setString(2, DatabaseSerializer.locationToString(parkour.getSpawnLocation()));
            preparedStatement.setString(3, DatabaseSerializer.locationToString(parkour.getStartLocation()));
            preparedStatement.setString(4, DatabaseSerializer.locationToString(parkour.getEndLocation()));
            preparedStatement.setInt(5, parkour.getExperience());

            if (update) preparedStatement.setInt(6, parkour.getDatabaseId());
            preparedStatement.execute();

            // async commit
            plugin.getDatabase().commit(() -> {
                if (update) {
                    // getting the database id isn't necessary on update
                    addOrUpdateParkour(parkour);
                    return;
                }

                try {
                    PreparedStatement getIdStatement = plugin.getDatabase().getConnection().prepareStatement(plugin.getQueries().getParkourGetIdQuery());
                    getIdStatement.setString(1, parkour.getName());

                    plugin.asyncSelect(getIdStatement, resultSet -> {
                        try {
                            if (resultSet.next()) {
                                parkour.setDatabaseId(resultSet.getInt(1));
                            }
                        }
                        catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }, success -> addOrUpdateParkour(parkour));
                }
                catch (SQLException e) {
                    e.printStackTrace();
                }
            });
            return true;
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void addOrUpdateParkour(Parkour parkour) {
        plugin.sync(() -> {
            plugin.addParkour(parkour);
            plugin.getParkourListener().getParkourSelectorGUI().update(plugin);
            parkourSelectorGUI.update(plugin);
            if (parkour.getLeaderboard() != null)
                parkour.getLeaderboard().refresh();
        });
    }

    private void selectParkour(Player player, Parkour parkour) {
        player.closeInventory();
        boolean alreadyEditing = parkourBuilders.values().stream()
                .anyMatch(parkourBuilder -> parkourBuilder.getDatabaseId() == parkour.getDatabaseId());
        if (alreadyEditing) {
            player.sendMessage(ChatColor.RED + "Somebody is already editing this parkour!");
            return;
        }

        Parkour.ParkourBuilder parkourBuilder = Parkour.builder()
                .databaseId(parkour.getDatabaseId())
                .name(parkour.getName())
                .spawnLocation(parkour.getSpawnLocation())
                .startLocation(parkour.getStartLocation())
                .endLocation(parkour.getEndLocation())
                .experience(parkour.getExperience())
                .markAsEditing();
        parkourBuilders.put(player, parkourBuilder);
        player.sendMessage(ChatColor.BLUE + "You can now edit the parkour '" + parkour.getName() + "'. Do '/parkour save' when the edits are made.");
        player.setGameMode(GameMode.CREATIVE);
    }

    private boolean checkArgument(String[] args, int index, String... matches) {
        if (args.length <= index)
            return false;
        String arg = args[index];
        for (String match : matches) {
            if (match.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

}
