package me.bintanq.quantumclan.module;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.Bukkit;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

/**
 * Schedules automatic war sessions based on war.yml config.
 *
 * Schedule types:
 *   DAILY   — every day at configured time
 *   WEEKLY  — specific day of week at configured time
 *   MONTHLY — specific day of month at configured time
 *
 * Sequence:
 *  1. Open registration (broadcast)
 *  2. After registration-open-minutes → close registration, start countdown
 *  3. After countdown-seconds → start war
 */
public class WarScheduler {

    private final QuantumClan plugin;
    private int scheduleTaskId = -1;

    public WarScheduler(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void schedule() {
        long delayTicks = calculateDelayTicks();
        if (delayTicks < 0) {
            plugin.getLogger().warning("[WarScheduler] Could not calculate next war time.");
            return;
        }

        plugin.getLogger().info("[WarScheduler] Next war in " + (delayTicks / 20) + " seconds.");

        scheduleTaskId = Bukkit.getScheduler().runTaskLater(plugin, this::openRegistration,
                delayTicks).getTaskId();
    }

    public String getNextWarTime() {
        long delayTicks = calculateDelayTicks();
        if (delayTicks < 0) return "Unknown";
        long seconds = delayTicks / 20;
        long hours   = seconds / 3600;
        long mins    = (seconds % 3600) / 60;
        return hours + "j " + mins + "m";
    }

    private void openRegistration() {
        if (plugin.getWarManager().getActiveSession() != null) {
            // War already running — reschedule for next period
            scheduleNext();
            return;
        }

        WarSession session = plugin.getWarManager().createSession();

        // Broadcast registration open
        plugin.broadcast(plugin.getWarConfigManager().getBroadcastRegistrationOpen());

        int registrationMinutes = plugin.getWarConfigManager().getRegistrationOpenMinutes();
        int countdownSeconds    = plugin.getWarConfigManager().getCountdownSeconds();

        // Schedule: close registration + start countdown
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getWarManager().getActiveSession() == null) {
                scheduleNext(); return;
            }
            session.setState(WarSession.State.COUNTDOWN);

            String closedMsg = plugin.getWarConfigManager().getBroadcastRegistrationClosed()
                    .replace("{seconds}", String.valueOf(countdownSeconds));
            plugin.broadcast(closedMsg);

            // Schedule: actually start war after countdown
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getWarManager().getActiveSession() == null) {
                    scheduleNext(); return;
                }
                if (plugin.getWarManager().getActiveSession().getRegisteredClanIds().size() < 2) {
                    plugin.getLogger().info("[WarScheduler] Not enough clans registered. War cancelled.");
                    plugin.getWarManager().setActiveSession(null);
                } else {
                    plugin.getWarManager().startWar();
                }
                scheduleNext();
            }, countdownSeconds * 20L).getTaskId();

        }, registrationMinutes * 60 * 20L).getTaskId();
    }

    private void scheduleNext() {
        long delayTicks = calculateDelayTicks();
        if (delayTicks <= 0) delayTicks = 20 * 60 * 60 * 24L; // fallback: 24h
        scheduleTaskId = Bukkit.getScheduler().runTaskLater(plugin,
                this::openRegistration, delayTicks).getTaskId();
    }

    /**
     * Calculates the delay in ticks until the next scheduled war.
     * Returns -1 if configuration is invalid.
     */
    private long calculateDelayTicks() {
        String type    = plugin.getWarConfigManager().getScheduleType();
        String timeStr = plugin.getWarConfigManager().getScheduleTime();

        LocalTime warTime;
        try {
            warTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            plugin.getLogger().warning("[WarScheduler] Invalid time format: " + timeStr);
            return -1;
        }

        ZoneId serverZone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(serverZone);
        ZonedDateTime next;

        switch (type) {
            case "DAILY" -> {
                next = now.with(warTime);
                if (!next.isAfter(now)) next = next.plusDays(1);
            }
            case "WEEKLY" -> {
                String dayStr = plugin.getWarConfigManager().getScheduleDay();
                DayOfWeek targetDay;
                try { targetDay = DayOfWeek.valueOf(dayStr); }
                catch (Exception e) { targetDay = DayOfWeek.SATURDAY; }

                next = now.with(TemporalAdjusters.nextOrSame(targetDay)).with(warTime);
                if (!next.isAfter(now)) {
                    next = now.with(TemporalAdjusters.next(targetDay)).with(warTime);
                }
            }
            case "MONTHLY" -> {
                int dayOfMonth = plugin.getWarConfigManager().getDayOfMonth();
                next = now.withDayOfMonth(Math.min(dayOfMonth, now.toLocalDate().lengthOfMonth()))
                        .with(warTime);
                if (!next.isAfter(now)) {
                    next = next.plusMonths(1)
                            .withDayOfMonth(Math.min(dayOfMonth, next.plusMonths(1)
                                    .toLocalDate().lengthOfMonth()));
                }
            }
            default -> { return -1; }
        }

        long secondsUntil = Duration.between(now, next).getSeconds();
        return secondsUntil * 20L;
    }

    public void cancel() {
        if (scheduleTaskId != -1) Bukkit.getScheduler().cancelTask(scheduleTaskId);
    }
}