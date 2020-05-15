package eu.izmoqwy.parkourchallenge;

import com.google.common.base.Preconditions;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ItemBuilder {

    private static ItemBuilder sharedInstance;

    private Material material;
    private short damage;

    private String displayName;
    private List<String> lore;

    public ItemBuilder material(Material material) {
        this.material = material;
        return this;
    }

    public ItemBuilder damage(short damage) {
        this.damage = damage;
        return this;
    }

    public ItemBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public ItemBuilder lore(String... lines) {
        if (lines == null) this.lore = new ArrayList<>();
        else this.lore = Arrays.asList(lines);
        return this;
    }

    public ItemStack toItemStack() {
        Preconditions.checkNotNull(material);
        ItemStack itemStack = new ItemStack(material, 1, damage);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (displayName != null)
            itemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        if (!lore.isEmpty()) {
            itemMeta.setLore(lore.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList()));
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private void reset() {
        this.material = null;
        this.damage = 0;
        this.displayName = null;
        this.lore = null;
    }

    public static ItemBuilder fresh() {
        if (sharedInstance == null) {
            sharedInstance = new ItemBuilder();
        }
        else {
            sharedInstance.reset();
        }
        return sharedInstance;
    }

}
