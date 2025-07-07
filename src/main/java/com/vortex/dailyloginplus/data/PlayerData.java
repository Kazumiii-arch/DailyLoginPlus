package com.vortex.dailyloginplus.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {

    private final UUID uniqueId;
    private LocalDate lastClaimDateMonthly;
    private int currentStreak;
    private int highestStreak;
    private LocalDateTime lastLoginDateTime; // CHANGE FROM LocalDate to LocalDateTime
    private long totalPlaytimeMinutes;
    private Map<Integer, Boolean> claimedPlaytimeMilestones;

    private LocalDate lastClaimDateWeekly; // Tracks the date of the last weekly reward claim
    private Map<String, LocalDate> lastClaimDateDonatorTiers; // Tracks the last claim date for each donator tier (e.g., "VIP" -> LocalDate)

    // Constructor for new players or when loading default
    public PlayerData(UUID uniqueId) {
        this.uniqueId = uniqueId;
        this.lastClaimDateMonthly = null;
        this.currentStreak = 0;
        this.highestStreak = 0;
        this.lastLoginDateTime = null; // Initialize as null
        this.totalPlaytimeMinutes = 0L;
        this.claimedPlaytimeMilestones = new HashMap<>();
        this.lastClaimDateWeekly = null;
        this.lastClaimDateDonatorTiers = new HashMap<>();
    }

    // Updated Constructor for loading existing data
    public PlayerData(UUID uniqueId, LocalDate lastClaimDateMonthly, int currentStreak, int highestStreak,
                      LocalDateTime lastLoginDateTime, // CHANGE TYPE HERE
                      long totalPlaytimeMinutes, Map<Integer, Boolean> claimedPlaytimeMilestones,
                      LocalDate lastClaimDateWeekly, Map<String, LocalDate> lastClaimDateDonatorTiers) {
        this.uniqueId = uniqueId;
        this.lastClaimDateMonthly = lastClaimDateMonthly;
        this.currentStreak = currentStreak;
        this.highestStreak = highestStreak;
        this.lastLoginDateTime = lastLoginDateTime; // Assign here
        this.totalPlaytimeMinutes = totalPlaytimeMinutes;
        this.claimedPlaytimeMilestones = claimedPlaytimeMilestones != null ? claimedPlaytimeMilestones : new HashMap<>();
        this.lastClaimDateWeekly = lastClaimDateWeekly;
        this.lastClaimDateDonatorTiers = lastClaimDateDonatorTiers != null ? lastClaimDateDonatorTiers : new HashMap<>();
    }

    // --- Getters & Setters ---
    public UUID getUniqueId() {
        return uniqueId;
    }

    public LocalDate getLastClaimDateMonthly() {
        return lastClaimDateMonthly;
    }

    public void setLastClaimDateMonthly(LocalDate lastClaimDateMonthly) {
        this.lastClaimDateMonthly = lastClaimDateMonthly;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getHighestStreak() {
        return highestStreak;
    }

    public void setHighestStreak(int highestStreak) {
        this.highestStreak = Math.max(this.highestStreak, highestStreak); // Always keep the highest
    }

    // Getter & Setter for lastLoginDateTime
    public LocalDateTime getLastLoginDateTime() {
        return lastLoginDateTime;
    }

    public void setLastLoginDateTime(LocalDateTime lastLoginDateTime) {
        this.lastLoginDateTime = lastLoginDateTime;
    }

    public long getTotalPlaytimeMinutes() {
        return totalPlaytimeMinutes;
    }

    public void setTotalPlaytimeMinutes(long totalPlaytimeMinutes) {
        this.totalPlaytimeMinutes = totalPlaytimeMinutes;
    }

    public void addPlaytime(long minutes) {
        this.totalPlaytimeMinutes += minutes;
    }

    public Map<Integer, Boolean> getClaimedPlaytimeMilestones() {
        return claimedPlaytimeMilestones;
    }

    public void markPlaytimeMilestoneClaimed(int milestone) {
        this.claimedPlaytimeMilestones.put(milestone, true);
    }

    public boolean hasClaimedPlaytimeMilestone(int milestone) {
        return this.claimedPlaytimeMilestones.getOrDefault(milestone, false);
    }

    // --- Getters & Setters for NEW Weekly & Donator Fields ---
    public LocalDate getLastClaimDateWeekly() {
        return lastClaimDateWeekly;
    }

    public void setLastClaimDateWeekly(LocalDate lastClaimDateWeekly) {
        this.lastClaimDateWeekly = lastClaimDateWeekly;
    }

    public Map<String, LocalDate> getLastClaimDateDonatorTiers() {
        return lastClaimDateDonatorTiers;
    }

    public void setLastClaimDateDonatorTier(String tier, LocalDate date) {
        this.lastClaimDateDonatorTiers.put(tier, date);
    }
    
    // Check if a specific donator tier reward has been claimed today
    public boolean hasClaimedDonatorRewardToday(String tier, LocalDate today) {
        LocalDate lastClaim = this.lastClaimDateDonatorTiers.get(tier);
        return lastClaim != null && lastClaim.isEqual(today);
    }
                                            }
