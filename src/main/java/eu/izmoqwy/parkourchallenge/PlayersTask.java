package eu.izmoqwy.parkourchallenge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayersTask extends BukkitRunnable {

    private ParkourChallenge plugin;

    public PlayersTask(ParkourChallenge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getJumpingPlayers().containsKey(player)) {
                float exp = player.getExp() + .1f;
                if (exp >= 1) {
                    player.setExp(0);
                    player.setLevel(player.getLevel() + 1);
                }
                else {
                    player.setExp(exp);
                }
            }
        }
    }

}
