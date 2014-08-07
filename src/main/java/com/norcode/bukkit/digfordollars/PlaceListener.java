package com.norcode.bukkit.digfordollars;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlaceListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        if (!event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
            Ore ore = Ore.getByMaterial(event.getBlockPlaced().getType());
            if (ore != null && ore.getIgnoreData() != -1) {
                event.getBlockPlaced().setData((byte) ore.getIgnoreData());
            }
        }
    }

}
