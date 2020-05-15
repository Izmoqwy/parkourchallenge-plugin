package eu.izmoqwy.parkourchallenge.gui;

import eu.izmoqwy.parkourchallenge.ItemBuilder;
import eu.izmoqwy.parkourchallenge.ParkourChallenge;
import eu.izmoqwy.parkourchallenge.model.Parkour;
import lombok.AccessLevel;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.function.BiConsumer;

public class ParkourSelectorGUI implements InventoryHolder {

    private List<Parkour> parkourList;
    private BiConsumer<Player, Parkour> onSelect;

    @Setter(AccessLevel.PRIVATE)
    private Inventory inventory;

    public ParkourSelectorGUI(BiConsumer<Player, Parkour> onSelect) {
        this.onSelect = onSelect;
    }

    @SuppressWarnings("deprecation")
    public void update(ParkourChallenge plugin) {
        inventory.clear();

        parkourList = plugin.getParkourList();
        for (int i = 0; i < parkourList.size(); i++) {
            Parkour parkour = parkourList.get(i);
            inventory.setItem(i, ItemBuilder.fresh()
                    .material(Material.WOOL)
                    .damage(DyeColor.BLUE.getWoolData())
                    .displayName(ChatColor.BLUE + parkour.getName())
                    .lore(
                            "&7This parkour gives &e" + parkour.getExperience() + " XP &7on complete"
                    )
                    .toItemStack());
        }
    }

    public void onClick(Player player, int slot) {
        if (onSelect == null || slot >= parkourList.size())
            return;
        onSelect.accept(player, parkourList.get(slot));
    }

    public static ParkourSelectorGUI create(ParkourChallenge plugin, BiConsumer<Player, Parkour> onSelect) {
        ParkourSelectorGUI parkourSelectorGUI = new ParkourSelectorGUI(onSelect);

        int rows = Math.max(1, (int) (Math.ceil(plugin.getParkourList().size() / 9.)));
        if (rows > 3) {
            // parkours are limited to 18 so 3 rows
            throw new UnsupportedOperationException();
        }

        Inventory inventory = Bukkit.createInventory(parkourSelectorGUI, rows * 9, ChatColor.BLUE + "Parkour Selector");
        parkourSelectorGUI.setInventory(inventory);
        parkourSelectorGUI.update(plugin);

        return parkourSelectorGUI;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

}
