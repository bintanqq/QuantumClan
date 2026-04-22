package me.bintanq.quantumclan.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility methods for time formatting and duration calculations.
 *
 * All methods are static and thread-safe.
 */
public final class TimeUtil {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private TimeUtil() {}

    // ── Duration formatting ───────────────────────────────────

    /**
     * Formats a duration in seconds into a human-readable string.
     * Examples:
     *   3661  → "1j 1m 1d"
     *   90    → "1m 30d"
     *   45    → "45d"
     *   0     → "0d"
     */
    public static String formatSeconds(long totalSeconds) {
        if (totalSeconds <= 0) return "0d";

        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0)   sb.append(hours).append("j ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("d");

        return sb.toString().trim();
    }

    /**
     * Formats a duration in milliseconds into a human-readable string.
     */
    public static String formatMillis(long millis) {
        return formatSeconds(millis / 1000L);
    }

    /**
     * Formats the duration between now and a future Instant.
     * Returns "Expired" if the instant is in the past.
     */
    public static String formatUntil(Instant future) {
        long seconds = future.getEpochSecond() - Instant.now().getEpochSecond();
        if (seconds <= 0) return "Expired";
        return formatSeconds(seconds);
    }

    /**
     * Formats the duration since a past Instant.
     * Returns "Sekarang" if the instant is in the future or present.
     */
    public static String formatSince(Instant past) {
        long seconds = Instant.now().getEpochSecond() - past.getEpochSecond();
        if (seconds <= 0) return "Sekarang";
        return formatSeconds(seconds) + " lalu";
    }

    // ── Cooldown helpers ──────────────────────────────────────

    /**
     * Returns the remaining seconds until a cooldown expires.
     * Returns 0 if the cooldown has already passed.
     *
     * @param lastUseMillis System.currentTimeMillis() when action last occurred
     * @param cooldownSec   Cooldown duration in seconds
     */
    public static long remainingCooldown(long lastUseMillis, long cooldownSec) {
        long elapsed = (System.currentTimeMillis() - lastUseMillis) / 1000L;
        return Math.max(0, cooldownSec - elapsed);
    }

    /**
     * Returns true if the cooldown has expired.
     *
     * @param lastUseMillis System.currentTimeMillis() when action last occurred
     * @param cooldownSec   Cooldown duration in seconds
     */
    public static boolean cooldownExpired(long lastUseMillis, long cooldownSec) {
        return remainingCooldown(lastUseMillis, cooldownSec) == 0;
    }

    // ── Ticks ─────────────────────────────────────────────────

    /**
     * Converts seconds to Bukkit scheduler ticks (1 second = 20 ticks).
     */
    public static long secondsToTicks(long seconds) {
        return seconds * 20L;
    }

    /**
     * Converts minutes to Bukkit scheduler ticks.
     */
    public static long minutesToTicks(long minutes) {
        return minutes * 60L * 20L;
    }

    /**
     * Converts ticks to seconds.
     */
    public static long ticksToSeconds(long ticks) {
        return ticks / 20L;
    }

    // ── Instant helpers ───────────────────────────────────────

    /**
     * Returns true if the given Instant has already passed.
     */
    public static boolean isPast(Instant instant) {
        return instant != null && Instant.now().isAfter(instant);
    }

    /**
     * Returns true if the given Instant is still in the future.
     */
    public static boolean isFuture(Instant instant) {
        return instant != null && Instant.now().isBefore(instant);
    }

    // ── Date formatting ───────────────────────────────────────

    /**
     * Formats an Instant as "dd/MM/yyyy".
     */
    public static String formatDate(Instant instant) {
        if (instant == null) return "-";
        return DATE_FMT.format(instant);
    }

    /**
     * Formats an Instant as "dd/MM/yyyy HH:mm".
     */
    public static String formatDateTime(Instant instant) {
        if (instant == null) return "-";
        return DATETIME_FMT.format(instant);
    }

    /**
     * Formats an Instant as "HH:mm:ss".
     */
    public static String formatTime(Instant instant) {
        if (instant == null) return "-";
        return TIME_FMT.format(instant);
    }

    // ── Duration object ───────────────────────────────────────

    /**
     * Returns a Duration between two instants. Always non-negative (absolute value).
     */
    public static Duration between(Instant a, Instant b) {
        Duration d = Duration.between(a, b);
        return d.isNegative() ? d.negated() : d;
    }
}