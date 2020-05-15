package eu.izmoqwy.parkourchallenge.listeners;

import eu.izmoqwy.parkourchallenge.ItemBuilder;
import eu.izmoqwy.parkourchallenge.ParkourChallenge;
import eu.izmoqwy.parkourchallenge.gui.ParkourSelectorGUI;
import eu.izmoqwy.parkourchallenge.model.Parkour;
import eu.izmoqwy.parkourchallenge.model.ParkourPlayer;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ParkourListener implements Listener {

    private final ItemStack selectionItem, teleportItem, cancelItem;

    private ParkourChallenge plugin;
    @Getter
    private ParkourSelectorGUI parkourSelectorGUI;

    /*
    this map is a workaround to detect if the player is within the radius of a starting point
    without having to iterate through all the parkours and check for the distance between its starting point and every player
    it stores the starting point and all the blocks around (3*3*3 -> 27 elt per parkour) for each parkour
    -> results in better performances
     */
    @Getter
    private Map<Vector, Integer> startingLocations;

    public ParkourListener(ParkourChallenge plugin) {
        this.selectionItem = ItemBuilder.fresh()
                .material(Material.EMPTY_MAP)
                .displayName("&eMap Selector")
                .lore(
                        "&f-----------------------------------",
                        "&7Browse parkour maps and teleport to them",
                        "&f-----------------------------------"
                )
                .toItemStack();
        this.teleportItem = ItemBuilder.fresh()
                .material(Material.RED_ROSE)
                .damage((short) 1)
                .displayName("&bTeleport")
                .lore(
                        "&f------------------------------",
                        "&7Teleport you back to the spawn point",
                        "&f------------------------------"
                )
                .toItemStack();
        this.cancelItem = ItemBuilder.fresh()
                .material(Material.LEVER)
                .displayName("&cGive up")
                .lore(
                        "&f----------------------",
                        "&7Your attempt will not count",
                        "&f----------------------"
                )
                .toItemStack();

        this.plugin = plugin;
        this.startingLocations = new HashMap<>();
    }

    public ParkourListener postDbLoad() {
        this.parkourSelectorGUI = ParkourSelectorGUI.create(plugin, plugin::joinParkour);
        return this;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.setInvulnerable(true);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);

        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear();
        playerInventory.setItem(4, selectionItem);

        player.setExp(0);
        player.setLevel(1);
        if (!plugin.getParkourPlayers().containsKey(player.getUniqueId())) {
            try {
                PreparedStatement preparedStatement = plugin.getDatabase().getConnection().prepareStatement(plugin.getQueries().getPlayerSelectQuery());
                preparedStatement.setString(1, player.getUniqueId().toString());
                plugin.asyncSelect(preparedStatement,
                        resultSet -> {
                            try {
                                int experience = 0;
                                if (resultSet.next()) {
                                    experience = resultSet.getInt("experience");
                                }
                                else {
                                    PreparedStatement preparedStatement1 = plugin.getDatabase().getConnection().prepareStatement(plugin.getQueries().getPlayerInsertQuery());
                                    preparedStatement1.setString(1, player.getUniqueId().toString());
                                    preparedStatement1.setInt(2, 0);
                                    preparedStatement1.execute();
                                    // commit is scheduled
                                }

                                int finalExperience = experience;
                                plugin.sync(() -> {
                                    plugin.getParkourPlayers().put(player.getUniqueId(), new ParkourPlayer(player.getUniqueId(), finalExperience));
                                    if (!plugin.getJumpingPlayers().containsKey(player))
                                        player.giveExp(finalExperience);
                                });
                            }
                            catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }, null);
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
        }
        else {
            player.giveExp(plugin.getParkourPlayers().get(player.getUniqueId()).getExperience());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getParkourCommand().getParkourBuilders().remove(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null &&
                (event.getItem().equals(selectionItem) || event.getItem().equals(teleportItem) || event.getItem().equals(cancelItem))) {

            event.setCancelled(true);
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                Player player = event.getPlayer();
                ItemStack item = event.getItem();

                if (item.equals(selectionItem)) {
                    player.openInventory(parkourSelectorGUI.getInventory());
                }
                else if (item.equals(teleportItem) && plugin.getJumpingPlayers().containsKey(player)) {
                    player.teleport(plugin.getJumpingPlayers().get(player).getSpawnLocation());
                }
                else if (item.equals(cancelItem)) {
                    plugin.endParkour(player, true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR || event.getWhoClicked().getGameMode() == GameMode.CREATIVE)
            return;

        if (event.getView().getTopInventory() != null && event.getView().getTopInventory().getHolder() instanceof ParkourSelectorGUI) {
            // cancel any click when the selection menu is opened
            event.setCancelled(true);
            if (event.getClickedInventory().equals(event.getView().getTopInventory()))
                ((ParkourSelectorGUI) event.getClickedInventory().getHolder()).onClick((Player) event.getWhoClicked(), event.getSlot());
        }

        // prevent the player from moving items in his quickbar
        else if (event.getClickedInventory().equals(event.getWhoClicked().getInventory()) && event.getSlotType() == InventoryType.SlotType.QUICKBAR) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
            event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
            event.setCancelled(true);
    }

    public void updatePlayerStatus(Player player, boolean jumping) {
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.setItem(3, jumping ? teleportItem : null);
        playerInventory.setItem(5, jumping ? cancelItem : null);
    }

    // using PlayerMoveEvent is actually faster than checking player movements in the PlayersTask
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // we don't really care if the player is going up or down we just want to know when he moves
        if (getDistanceXZ(event.getFrom(), event.getTo()) >= 0.05) {
            Player player = event.getPlayer();
            Parkour parkour;
            if ((parkour = plugin.getJumpingPlayers().get(player)) != null) {
                if (player.getLocation().distance(parkour.getEndLocation()) <= 2) {
                    plugin.endParkour(player, false);
                }
            }
            else if (startingLocations.containsKey(toFloorVector(player.getLocation()))
                    && !plugin.getParkourCommand().getParkourBuilders().containsKey(player)) {
                // recalling toFloorVector here makes sense because this line will be reached less often
                // than the checks so we may not want to set a variable when calling toFloorVector in the condition above
                int parkourId = startingLocations.get(toFloorVector(player.getLocation()));
                plugin.joinParkour(player, plugin.getParkourList().stream()
                        .filter(parkour1 -> parkour1.getDatabaseId() == parkourId).findFirst().orElse(null));
            }
        }
    }

    public Vector toFloorVector(Location location) {
        return new Vector(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private double getDistanceXZ(Location from, Location to) {
        return Math.sqrt(NumberConversions.square(from.getX() - to.getX()) + NumberConversions.square(from.getZ() - to.getZ()));
    }

}
