package com.norcode.bukkit.digfordollars;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerTally {
    DigForDollars plugin;
    Ore ore = null;
    int count = 0;
    UUID playerId;

    private long lastAdded = 0;

    public void add(Ore o) {

        if (this.ore != null && this.ore != o) {
            doReward();
        }
        ore = o;
        count ++;
        lastAdded = System.currentTimeMillis();
    }

    public void doReward() {
        double paid = flushPay();
        Player player = plugin.getServer().getPlayer(playerId);

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getPaidMessage().format(new Object[] {
                        plugin.getEconomy().format(paid),
                        count,
                        count > 1 ? ore.getDisplayNamePlural() : ore.getDisplayName()
                })
        ));

        ore = null;
        count = 0;
    }

    public PlayerTally(DigForDollars plugin, UUID playerId) {
        this.plugin = plugin;
        this.playerId = playerId;
    }

    public double flushPay() {
        Player player = plugin.getServer().getPlayer(this.playerId);
        double total = ore.getValue() * count;
        plugin.getEconomy().depositPlayer(player.getName(), total);
        return total;
    }

    public boolean isReady(long now) {
        return now - lastAdded > 1000;
    }

}
