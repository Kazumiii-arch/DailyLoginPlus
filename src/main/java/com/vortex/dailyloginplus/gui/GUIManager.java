package com.vortex.dailyloginplus.gui;

import com.vortex.dailyloginplus.DailyLoginPlus;
import com.vortex.dailyloginplus.config.ConfigManager;
import com.vortex.dailyloginplus.data.DataManager;
import com.vortex.dailyloginplus.data.PlayerData;
import com.vortex.dailyloginplus.rewards.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import me.clip.placeholderapi.PlaceholderAPI; // Import for PlaceholderAPI

public class GUIManager {

    private final DailyLoginPlus plugin;
    private final ConfigManager configManager;
    private final DataManager dataManager;
    private final RewardManager rewardManager;
    private final ZoneId serverTimeZone;

    public GUIManager(DailyLoginPlus plugin, DataManager dataManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.dataManager = dataManager;
        this.rewardManager = rewardManager;
        
        this.serverTimeZone = configManager.getServerTimeZone();
    }

    // --- Main Menu GUI ---
    public void openMainMenu(Player player) {
        String title = configManager.getRawMessage("gui-settings.main-menu-title");
        Inventory gui = Bukkit.createInventory(null, 9 * 3, title); // 3 rows for main menu

        // Monthly Login Calendar Button
        ItemStack monthlyIcon = createGuiItem(
            Material.CLOCK,
            configManager.getRawMessage("monthly-calendar.gui-icon.name"),
            configManager.getConfig().getStringList("monthly-calendar.gui-icon.lore"),
            player 
        );
        gui.setItem(10, monthlyIcon);

        // Weekly Login Button
        ItemStack weeklyIcon = createGuiItem(
            Material.CALENDAR,
            configManager.getRawMessage("weekly-login.gui-icon.name"),
            configManager.getConfig().getStringList("weekly-login.gui-icon.lore"),
            player
        );
        gui.setItem(12, weeklyIcon);

        // Playtime Rewards Button
        ItemStack playtimeIcon = createGuiItem(
            Material.DIAMOND_PICKAXE,
            configManager.getRawMessage("playtime-rewards.gui-icon.name"),
            configManager.getConfig().getStringList("playtime-rewards.gui-icon.lore"),
            player
        );
        gui.setItem(14, playtimeIcon);

        // Donator Rewards Button
        ItemStack donatorIcon = createGuiItem(
            Material.GOLD_INGOT,
            configManager.getRawMessage("donator-rewards.gui-icon.name"),
            configManager.getConfig().getStringList("donator-rewards.gui-icon.lore"),
            player
        );
        gui.setItem(16, donatorIcon);

        // Filler items (optional)
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null, player);
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
    }

    // --- Monthly Calendar GUI ---
    public void openMonthlyCalendar(Player player) {
        String title = configManager.getRawMessage("gui-settings.monthly-title");
        int rows = configManager.getConfig().getInt("monthly-calendar.gui-rows", 6);
        Inventory gui = Bukkit.createInventory(null, 9 * rows, title);

        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        LocalDate today = LocalDate.now(serverTimeZone);
        int currentDayOfMonth = today.getDayOfMonth();
        LocalDate lastClaimDateMonthly = playerData.getLastClaimDateMonthly();

        // Populate days
        for (int day = 1; day <= today.lengthOfMonth(); day++) {
            Material itemMaterial;
            String itemName;
            List<String> itemLore = new ArrayList<>();
            List<String> rewardsForDay = configManager.getConfig().getStringList("monthly-calendar.rewards." + day);

            // Determine item state: Claimed, Claimable, Locked
            if (lastClaimDateMonthly != null && lastClaimDateMonthly.getDayOfMonth() == day && lastClaimDateMonthly.getMonth() == today.getMonth() && lastClaimDateMonthly.getYear() == today.getYear()) {
                // Claimed today (or specific day in this month in this year)
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.claimed-item.material", "LIME_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.claimed-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.claimed-item.lore"));
            } else if (day == currentDayOfMonth && (lastClaimDateMonthly == null || !lastClaimDateMonthly.isEqual(today))) {
                // Current day, not yet claimed
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.unclaimed-item.material", "YELLOW_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.unclaimed-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.unclaimed-item.lore"));
                itemLore.add(""); // Spacer
                itemLore.add(configManager.getRawMessage("gui-item-current-day-lore"));
            } else if (day < currentDayOfMonth) {
                // Past day, unclaimed (missed) - can be displayed as locked or missed
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.locked-item.material", "RED_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.locked-item.name") + " &7(Missed Day " + day + ")";
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.locked-item.lore"));
            } else {
                // Future day (locked)
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.locked-item.material", "RED_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.locked-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.locked-item.lore"));
            }
            
            // Add reward details to lore
            itemLore.add(""); // Spacer
            itemLore.add("&6Rewards:");
            if (rewardsForDay.isEmpty()) {
                itemLore.add("  &7- None configured");
            } else {
                for (String reward : rewardsForDay) {
                    itemLore.add("  &7- " + reward); // Show raw reward strings, could be parsed for better display
                }
            }


            ItemStack dayItem = createGuiItem(itemMaterial, "&bDay " + day + " " + itemName, itemLore, player);
            // Add glow if current day and configured
            if (day == currentDayOfMonth && (lastClaimDateMonthly == null || !lastClaimDateMonthly.isEqual(today)) && configManager.getConfig().getBoolean("gui-settings.current-day-glow", true)) {
                dayItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1); // Add a fake enchantment for glow
                ItemMeta meta = dayItem.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); // Hide enchantment name
                    dayItem.setItemMeta(meta);
                }
            }
            
            gui.setItem(day - 1, dayItem); // Day 1 goes to slot 0, Day 2 to slot 1, etc.
        }

        // Add clock item showing server time
        ItemStack clockItem = createGuiItem(
            Material.COMPASS,
            configManager.getRawMessage("gui-item-server-time")
                .replace("%server_time%", LocalDateTime.now(serverTimeZone).format(DateTimeFormatter.ofPattern("HH:mm:ss"))),
            null,
            player 
        );
        gui.setItem(gui.getSize() - 1, clockItem); // Last slot

        player.openInventory(gui);
    }

    // --- Weekly Login GUI ---
    public void openWeeklyCalendar(Player player) {
        String title = configManager.getRawMessage("gui-settings.weekly-title");
        Inventory gui = Bukkit.createInventory(null, 9 * 2, title); // 2 rows for 7 days + filler

        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        LocalDate today = LocalDate.now(serverTimeZone);
        DayOfWeek currentDayOfWeek = today.getDayOfWeek(); 
        LocalDate lastClaimDateWeekly = playerData.getLastClaimDateWeekly();

        // Calculate the start of the current week (e.g., Monday of this week)
        // Note: Java's DayOfWeek is MONDAY (1) to SUNDAY (7). 
        // We want the start of the week. If currentDayOfWeek is MONDAY (1), minus 0 days. If TUESDAY (2), minus 1 day.
        LocalDate startOfWeek = today.minusDays(currentDayOfWeek.getValue() - 1); 

        for (int i = 0; i < 7; i++) {
            // Day represented by this slot (e.g., if i=0, it's Monday; i=6 is Sunday)
            DayOfWeek dayEnum = DayOfWeek.of((i % 7) + 1); // 1 = MONDAY, 7 = SUNDAY
            LocalDate dateForSlot = startOfWeek.plusDays(i);

            Material itemMaterial;
            String itemName;
            List<String> itemLore = new ArrayList<>();
            // Rewards are configured by day number 1-7 in config, corresponding to DayOfWeek.getValue()
            List<String> rewardsForDay = configManager.getConfig().getStringList("weekly-login.rewards." + dayEnum.getValue());

            // Determine item state: Claimed, Claimable, Locked
            boolean isCurrentDayInRealLife = dateForSlot.isEqual(today);
            boolean hasClaimedWeeklyThisCycle = (lastClaimDateWeekly != null && !lastClaimDateWeekly.isBefore(startOfWeek));


            if (isCurrentDayInRealLife && !hasClaimedWeeklyThisCycle) {
                // Current day, not yet claimed for this weekly cycle
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.unclaimed-item.material", "YELLOW_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.unclaimed-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.unclaimed-item.lore"));
                itemLore.add(""); // Spacer
                itemLore.add(configManager.getRawMessage("gui-item-current-day-lore"));
            } else if (hasClaimedWeeklyThisCycle && dateForSlot.isEqual(lastClaimDateWeekly)) {
                // This specific day was claimed (if you track per-day weekly claims)
                // For a *single* weekly claim, this implies it's claimed if lastClaimDateWeekly is within this cycle.
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.claimed-item.material", "LIME_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.claimed-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.claimed-item.lore"));
            }
            else { // Day is either in the past AND already claimed, or in the future
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.locked-item.material", "RED_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.locked-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.locked-item.lore"));
            }
            
            // Add reward details to lore
            itemLore.add(""); // Spacer
            itemLore.add("&6Rewards for " + dayEnum.name() + ":");
            if (rewardsForDay.isEmpty()) {
                itemLore.add("  &7- None configured");
            } else {
                for (String reward : rewardsForDay) {
                    itemLore.add("  &7- " + reward); // Show raw reward strings
                }
            }

            ItemStack dayItem = createGuiItem(itemMaterial, "&b" + dayEnum.name() + " (" + dateForSlot.format(DateTimeFormatter.ofPattern("MMM dd")) + ") " + itemName, itemLore, player);
            // Add glow if current day and configured
            if (isCurrentDayInRealLife && !hasClaimedWeeklyThisCycle && configManager.getConfig().getBoolean("gui-settings.current-day-glow", true)) {
                dayItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
                ItemMeta meta = dayItem.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    dayItem.setItemMeta(meta);
                }
            }
            gui.setItem(i, dayItem); // Place items in first row
        }

        // Add back button
        ItemStack backButton = createGuiItem(Material.ARROW, "&7Back to Main Menu", null, player);
        gui.setItem(gui.getSize() - 1, backButton);

        player.openInventory(gui);
    }

    // --- Playtime Rewards GUI ---
    public void openPlaytimeRewardsGUI(Player player) {
        String title = configManager.getRawMessage("gui-settings.playtime-title");
        Inventory gui = Bukkit.createInventory(null, 9 * 3, title); // Example: 3 rows

        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        long totalPlaytime = playerData.getTotalPlaytimeMinutes();

        // Get configured playtime milestones and sort them
        Set<Integer> milestones = new TreeSet<>(); // Use TreeSet to keep them sorted
        if (configManager.getConfig().contains("playtime-rewards.rewards")) {
            Objects.requireNonNull(configManager.getConfig().getConfigurationSection("playtime-rewards.rewards")).getKeys(false).forEach(key -> {
                try {
                    milestones.add(Integer.parseInt(key));
                } catch (NumberFormatException ignored) {}
            });
        }

        int slot = 0;
        for (int milestone : milestones) {
            Material itemMaterial;
            String itemName;
            List<String> itemLore = new ArrayList<>();
            List<String> rewardsForMilestone = configManager.getConfig().getStringList("playtime-rewards.rewards." + milestone);

            boolean hasClaimed = playerData.hasClaimedPlaytimeMilestone(milestone);
            boolean canClaim = totalPlaytime >= milestone && !hasClaimed;

            if (canClaim) {
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.unclaimed-item.material", "YELLOW_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.unclaimed-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.unclaimed-item.lore"));
                itemLore.add("");
                itemLore.add("&aClick to claim!");
            } else if (hasClaimed) {
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.claimed-item.material", "LIME_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.claimed-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.claimed-item.lore"));
            } else {
                itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.locked-item.material", "RED_STAINED_GLASS_PANE"));
                itemName = configManager.getRawMessage("gui-settings.locked-item.name");
                itemLore.addAll(configManager.getConfig().getStringList("gui-settings.locked-item.lore"));
                long minutesRemaining = milestone - totalPlaytime;
                itemLore.add("");
                itemLore.add(configManager.getRawMessage("playtime-not-reached")
                    .replace("%time_remaining%", String.valueOf(minutesRemaining)));
            }
            
            itemLore.add("");
            itemLore.add("&6Milestone: &b" + milestone + " Minutes");
            itemLore.add("&7Your Playtime: &e" + totalPlaytime + " Minutes");
            itemLore.add("");
            itemLore.add("&6Rewards:");
            if (rewardsForMilestone.isEmpty()) {
                itemLore.add("  &7- None configured");
            } else {
                for (String reward : rewardsForMilestone) {
                    itemLore.add("  &7- " + reward);
                }
            }

            ItemStack milestoneItem = createGuiItem(itemMaterial, "&b" + milestone + " Min. Playtime Reward " + itemName, itemLore, player);
            if (canClaim && configManager.getConfig().getBoolean("gui-settings.current-day-glow", true)) { // Re-using glow setting
                milestoneItem.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
                ItemMeta meta = milestoneItem.getItemMeta();
                if (meta != null) {
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    milestoneItem.setItemMeta(meta);
                }
            }
            gui.setItem(slot++, milestoneItem);
        }

        // Back button
        ItemStack backButton = createGuiItem(Material.ARROW, "&7Back to Main Menu", null, player);
        gui.setItem(gui.getSize() - 1, backButton);
        
        player.openInventory(gui);
    }

    // --- Donator Rewards GUI ---
    public void openDonatorRewardsGUI(Player player) {
        String title = configManager.getRawMessage("gui-settings.donator-title");
        Inventory gui = Bukkit.createInventory(null, 9 * 3, title); // Example: 3 rows

        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        LocalDate today = LocalDate.now(serverTimeZone);

        // Get configured donator tiers (VIP, MVP, etc.)
        List<String> donatorTiers = new ArrayList<>();
        if (configManager.getConfig().contains("donator-rewards.rewards")) {
            donatorTiers.addAll(Objects.requireNonNull(configManager.getConfig().getConfigurationSection("donator-rewards.rewards")).getKeys(false));
        }

        int slot = 0;
        for (String tierName : donatorTiers) {
            String permissionNode = configManager.getConfig().getString("donator-rewards.rewards." + tierName + ".permission");
            List<String> rewardsForTier = configManager.getConfig().getStringList("donator-rewards.rewards." + tierName + ".commands");

            Material itemMaterial;
            String itemName;
            List<String> itemLore = new ArrayList<>();

            // Check if player has permission for this tier AND if claimed today
            boolean hasPermission = player.hasPermission(permissionNode);
            boolean claimedToday = playerData.hasClaimedDonatorRewardToday(tierName, today);

            if (hasPermission) {
                if (!claimedToday) {
                    itemMaterial = Material.valueOf(configManager.getConfig().getString("gui-settings.unclaimed-item.material", "YELLOW_STAINED_GLASS_PANE"));
                    itemName = configManager.getRawMessage("gui-settings.unclaimed-item.name");
                    itemLore.addAll(configManager.getConfig().getStringList("gui-settings.unclaimed-item.lore"));
                    itemLore.add("");
                    itemLore.add("&aClick to claim your " + tierName + " reward!"); // LINE 376 (approx) if lines above are removed
                } else {
         
