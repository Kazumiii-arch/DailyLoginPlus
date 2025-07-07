package com.vortex.dailyloginplus.placeholder;

import com.vortex.dailyloginplus.DailyLoginPlus;
import com.vortex.dailyloginplus.data.DataManager;
import com.vortex.dailyloginplus.data.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ArrayList; // For sorting
import java.util.Collections; // For sorting

public class DailyLoginPlusExpansion extends PlaceholderExpansion {

    private final DailyLoginPlus plugin;
    private final DataManager dataManager;

    public DailyLoginPlusExpansion(DailyLoginPlus plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dailyloginplus"; // %dailyloginplus_...%
    }

    @Override
    public @NotNull String getAuthor() {
        return "Vortex";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean canRegister() {
        return true; // Always true, as we handle dependencies
    }

    @Override
    public boolean persist() {
        return true; // Data should persist across reloads
    }

    @Override
    public String onPlaceholderRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return ""; // Offline player context not supported for all placeholders
        }

        PlayerData playerData = dataManager.getPlayerData(player.getUniqueId());
        LocalDate today = LocalDate.now(plugin.getConfigManager().getServerTimeZone());

        switch (params.toLowerCase()) {
            case "current_streak":
                return String.valueOf(playerData.getCurrentStreak());
            case "highest_streak":
                return String.valueOf(playerData.getHighestStreak());
            case "total_playtime":
                return String.valueOf(playerData.getTotalPlaytimeMinutes());
            case "monthly_last_claimed":
                return playerData.getLastClaimDateMonthly() != null ?
                       playerData.getLastClaimDateMonthly().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) :
                       "Never";
            case "monthly_claimed_today":
                boolean claimedMonthlyToday = (playerData.getLastClaimDateMonthly() != null && playerData.getLastClaimDateMonthly().isEqual(today));
                return claimedMonthlyToday ? "Yes" : "No";
            case "weekly_last_claimed":
                return playerData.getLastClaimDateWeekly() != null ?
                       playerData.getLastClaimDateWeekly().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) :
                       "Never";
            case "weekly_claimed_this_cycle":
                LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
                boolean claimedWeeklyThisCycle = (playerData.getLastClaimDateWeekly() != null && !playerData.getLastClaimDateWeekly().isBefore(startOfWeek));
                return claimedWeeklyThisCycle ? "Yes" : "No";
            case "playtime_milestones_claimed":
                StringBuilder claimedPlaytimeSummary = new StringBuilder();
                boolean firstMilestone = true;
                
                List<Integer> sortedMilestones = new ArrayList<>(playerData.getClaimedPlaytimeMilestones().keySet());
                Collections.sort(sortedMilestones); // Sort for consistent output

                for (int milestone : sortedMilestones) {
                    if (playerData.getClaimedPlaytimeMilestones().getOrDefault(milestone, false)) {
                        if (!firstMilestone) claimedPlaytimeSummary.append(", ");
                        claimedPlaytimeSummary.append(milestone).append("m");
                        firstMilestone = false;
                    }
                }
                return claimedPlaytimeSummary.length() > 0 ? claimedPlaytimeSummary.toString() : "None";
            case "donator_rewards_status":
                StringBuilder donatorClaimsSummary = new StringBuilder();
                boolean firstTier = true;
                
                List<String> sortedTiers = new ArrayList<>(playerData.getLastClaimDateDonatorTiers().keySet());
                Collections.sort(sortedTiers); // Sort tiers alphabetically

                for (String tier : sortedTiers) {
                    if (!firstTier) donatorClaimsSummary.append(", ");
                    donatorClaimsSummary.append(tier).append(": ");
                    if (playerData.hasClaimedDonatorRewardToday(tier, today)) {
                        donatorClaimsSummary.append("Claimed Today");
                    } else {
                        donatorClaimsSummary.append("Not Claimed Today");
                    }
                    firstTier = false;
                }
                return donatorClaimsSummary.length() > 0 ? donatorClaimsSummary.toString() : "None";

            // Add other specific placeholders as needed
            // e.g. %dailyloginplus_has_claimed_monthly_today% -> true/false
            // e.g. %dailyloginplus_monthly_day_status_1% -> CLAIMED/UNCLAIMED/LOCKED

            default:
                return null; // Placeholder is invalid
        }
    }
                                                           }
