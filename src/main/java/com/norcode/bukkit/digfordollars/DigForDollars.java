package com.norcode.bukkit.digfordollars;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DigForDollars extends JavaPlugin implements Listener {

    public static Economy economy = null;
    private HashMap<UUID, PlayerTally> tallies = new HashMap<UUID, PlayerTally>();
    private boolean requirePermissions = true;
    private MessageFormat paidMessage;
    private static Method itemCausesDrops;
    long checkTimeout = 20;

    public Economy getEconomy() {
        return economy;
    }


    private void reflect() {
        Block b = getServer().getWorlds().get(0).getHighestBlockAt(0,0);
        Class cbBlockClass = b.getClass();
        try {
            itemCausesDrops = cbBlockClass.getDeclaredMethod("itemCausesDrops", ItemStack.class);
            itemCausesDrops.setAccessible(true);
            getLogger().info("ItemCausesDrops  method: " + itemCausesDrops);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("digfordollars")) {
            if (args.length ==1 && args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    sender.sendMessage("DigForDollars configuration reloaded.");
            } else {
                sender.sendMessage(command.getUsage());
            }
            return true;
        }
        return false;
    }

    @Override
    public void reloadConfig() {
        getLogger().info("Reloading Config for DigForDollars");
        BlockPlaceEvent.getHandlerList().unregister((Plugin) this);
        super.reloadConfig();
        requirePermissions = getConfig().getBoolean("require-permissions", true);
        paidMessage = new MessageFormat(getConfig().getString("messages.paid"));
        checkTimeout = getConfig().getLong("payout-delay", 20);
        loadOres();
        boolean listen = false;
        for (Ore ore: Ore.values()) {
            if (ore.getIgnoreData() != -1) {
                listen = true;
                break;
            }
        }
        if (listen) {
            getServer().getPluginManager().registerEvents(new PlaceListener(), this);
        }
    }

    /**
     * load ore definitions:
     *
     * config.yml example:
     *
     *     ores:
     *       glass:
     *         display: "glass"
     *         material: [GLASS, STAINED_GLASS],
     *         value: 0.60
     *       glass-pane:
     *         display: ["glass pane", "panes of glass"]
     *         material: 160
     *         value: 0.1
     *
     */

    private void loadOres() {
        Ore.reset();
        Permission wildcard = getServer().getPluginManager().getPermission("digfordollars.payfor.*");
        if (wildcard == null) {
            getLogger().warning("No wildcard perm?");
            wildcard = new Permission("digfordollars.payfor.*", PermissionDefault.TRUE);
            wildcard.setDescription("Allow earning money from all registered ores");
            getServer().getPluginManager().addPermission(wildcard);
        } else {
            getLogger().info("found wildcard, clearing children");
            Map<String, Boolean> children = wildcard.getChildren();
            if (children != null) {
                Iterator<String> it = wildcard.getChildren().keySet().iterator();
                while (it.hasNext()) {
                    String ps = it.next();
                    getServer().getPluginManager().removePermission(ps);
                    getLogger().info("Removed " + ps);
                    it.remove();
                }
            }
        }
        wildcard.recalculatePermissibles();

        ConfigurationSection oreConfig = getConfig().getConfigurationSection("ores");
        ConfigurationSection cfg;
        for (String key: oreConfig.getKeys(false)) {
            cfg = oreConfig.getConfigurationSection(key);

            // get the material(s)
            List<String> matInputs = new ArrayList<String>();
            if (cfg.isList("material")) {
                matInputs = cfg.getStringList("material");
            } else {
                matInputs.add(cfg.getString("material"));
            }

            EnumSet<Material> mats = EnumSet.noneOf(Material.class);
            for (String matIn: matInputs) {
                Material mat = Material.matchMaterial(matIn);
                if (mat != null) {
                    mats.add(mat);
                } else {
                    getLogger().warning("Ignoring material '" + matIn + "' for ore '" + key + "'.");
                }
            }
            if (mats.size() == 0) {
                getLogger().warning("Ignoring ore '" + key + "', no valid materials.");
                continue;
            }

            // get the display name(s)
            String displayName = null;
            String displayNamePlural = null;
            if (cfg.isList("display")) {
                List<String> dispNames = cfg.getStringList("display");
                displayName = dispNames.get(0);
                try {
                    displayNamePlural = dispNames.get(1);
                } catch (IndexOutOfBoundsException ex) {
                    displayNamePlural = displayName;
                }
            } else {
                displayName = cfg.getString("display", key.replace("_", " ").replace("-", " "));
                displayNamePlural = displayName;
            }

            double value = cfg.getDouble("value");
            boolean checkDrops = cfg.getBoolean("check-drops", true);
            short ignoreData = (short) cfg.getInt("ignore-data", -1);
            Ore ore = new Ore(key, displayName,  displayNamePlural, value, true, mats, ignoreData);
            Permission perm = new Permission("digfordollars.payfor." + key);
            perm.addParent(wildcard, true);
            perm.recalculatePermissibles();
            getLogger().info("Registered Ore: " + ore);
        }
        wildcard.recalculatePermissibles();
    }


    @Override
    public void onEnable() {
        if (!this.setupEconomy()) {
            getLogger().severe("No Economy plugin found.  DigForDollars is disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        reloadConfig();
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                processTallies();
            }
        }, checkTimeout, checkTimeout);
        reflect();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {

        Player player = event.getPlayer();

        if (player.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }

        Ore ore = Ore.getByMaterial(event.getBlock().getType());
        if  (ore == null) {
            return;
        }

        boolean pay = true;
        if (ore.isCheckDrops()) {
            pay = false;
            try {
                pay = (Boolean) itemCausesDrops.invoke(event.getBlock(), player.getItemInHand());
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        if (ore.getIgnoreData() != -1) {
            if (event.getBlock().getData() == ore.getIgnoreData()) {
                pay = false;
            }
        }

        if (pay && !player.getItemInHand().getEnchantments().containsKey(Enchantment.SILK_TOUCH)) {
            if (!requirePermissions || player.hasPermission(ore.getRequiredPermission())) {
                getPlayerTally(player).add(ore);
            }
        }
    }

    private PlayerTally getPlayerTally(Player player) {
        PlayerTally tally = tallies.get(player.getUniqueId());
        if (tally == null) {
            tally = new PlayerTally(this, player.getUniqueId());
            tallies.put(player.getUniqueId(), tally);
        }
        return tally;
    }

    public void processTallies() {
        Iterator<Map.Entry<UUID, PlayerTally>> it = tallies.entrySet().iterator();
        long now = System.currentTimeMillis();
        while (it.hasNext()) {
            Map.Entry<String, PlayerTally> entry = (Map.Entry) it.next();
            if (entry.getValue().isReady(now)) {
                entry.getValue().doReward();
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerTally tally = tallies.get(event.getPlayer().getUniqueId());
        if (tally != null) {
            tally.flushPay();
        }
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        return (economy != null);
    }

    public MessageFormat getPaidMessage() {
        return paidMessage;
    }
}