package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.command.sub.SubCommand;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Arrays;

/**
 * Handles /qclanadmin hall <subcommand>.
 *
 * All player-facing text comes from messages.yml — no hardcoded strings.
 * Added: setvaultblock / clearvaultblock for the physical vault block feature.
 */
public class AdminHallCommand implements SubCommand {

    private final QuantumClan plugin;

    public AdminHallCommand(QuantumClan plugin) {
        this.plugin = plugin;
    }

    public void execute(Player player, String[] args) {
        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "setregion"      -> handleSetRegion(player);
            case "setschematic"   -> handleSetSchematic(player, Arrays.copyOfRange(args, 1, args.length));
            case "paste"          -> handlePaste(player);
            case "addnpc"         -> handleAddNpc(player, Arrays.copyOfRange(args, 1, args.length));
            case "removenpc"      -> handleRemoveNpc(player, Arrays.copyOfRange(args, 1, args.length));
            case "listnpc"        -> handleListNpc(player);
            case "setcost"        -> handleSetCost(player, Arrays.copyOfRange(args, 1, args.length));
            case "grant"          -> handleGrant(player, Arrays.copyOfRange(args, 1, args.length));
            case "revoke"         -> handleRevoke(player, Arrays.copyOfRange(args, 1, args.length));
            case "info"           -> handleInfo(player);
            case "reload"         -> handleReload(player);
            case "setvaultblock"  -> handleSetVaultBlock(player);
            case "clearvaultblock"-> handleClearVaultBlock(player);
            default               -> sendHelp(player);
        }
    }

    // ── setregion ─────────────────────────────────────────────

    private void handleSetRegion(Player player) {
        if (!plugin.getClanHallManager().hasPendingCorner1(player.getUniqueId())) {
            plugin.getClanHallManager().setPendingCorner1(player.getUniqueId(), player.getLocation());
            plugin.sendMessage(player, "hall.admin.setregion-corner1",
                    "{x}", String.valueOf(player.getLocation().getBlockX()),
                    "{y}", String.valueOf(player.getLocation().getBlockY()),
                    "{z}", String.valueOf(player.getLocation().getBlockZ()));
        } else {
            Location corner1 = plugin.getClanHallManager().consumePendingCorner1(player.getUniqueId());
            Location corner2 = player.getLocation();

            double minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
            double minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
            double minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
            double maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
            double maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
            double maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

            Location min = new Location(corner1.getWorld(), minX, minY, minZ);
            Location max = new Location(corner1.getWorld(), maxX, maxY, maxZ);

            plugin.getHallConfigManager().setRegionCorner1(min);
            plugin.getHallConfigManager().setRegionCorner2(max);

            if (plugin.getHookManager().isWorldGuardEnabled()) {
                try {
                    me.bintanq.quantumclan.hook.WorldGuardWrapper.createRegion(
                            plugin.getHallConfigManager().getWorldGuardRegionName(), min, max);
                    plugin.sendMessage(player, "hall.admin.worldguard-region-created",
                            "{region}", plugin.getHallConfigManager().getWorldGuardRegionName());
                } catch (NoClassDefFoundError | Exception e) {
                    plugin.getLogger().warning("[ClanHall] WorldGuard region creation failed: " + e.getMessage());
                }
            }

            plugin.sendMessage(player, "hall.admin.setregion-done",
                    "{x1}", String.valueOf((int) minX), "{y1}", String.valueOf((int) minY), "{z1}", String.valueOf((int) minZ),
                    "{x2}", String.valueOf((int) maxX), "{y2}", String.valueOf((int) maxY), "{z2}", String.valueOf((int) maxZ));
        }
    }

    // ── setschematic ──────────────────────────────────────────

    private void handleSetSchematic(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "hall.admin.usage-setschematic");
            return;
        }
        String filename = args[0];
        File schematicFile = new File(new File(plugin.getDataFolder(), "halls/schematics"), filename);
        if (!schematicFile.exists()) {
            plugin.sendMessage(player, "hall.admin.schematic-not-found", "{file}", filename);
            return;
        }
        plugin.getHallConfigManager().setSchematicFile(filename);
        plugin.sendMessage(player, "hall.admin.schematic-set", "{file}", filename);
    }

    // ── paste ─────────────────────────────────────────────────

    private void handlePaste(Player player) {
        if (plugin.getSchematicProvider() == null) {
            plugin.sendMessage(player, "hall.admin.no-schematic-provider");
            return;
        }
        String filename = plugin.getHallConfigManager().getSchematicFile();
        File schematicFile = new File(new File(plugin.getDataFolder(), "halls/schematics"), filename);
        if (!schematicFile.exists()) {
            plugin.sendMessage(player, "hall.admin.schematic-not-found", "{file}", filename);
            return;
        }
        Location origin = plugin.getHallConfigManager().getSchematicPasteOrigin();
        if (origin == null) {
            plugin.sendMessage(player, "hall.admin.paste-origin-not-set");
            return;
        }
        plugin.sendMessage(player, "hall.admin.paste-started", "{file}", filename);
        plugin.getSchematicProvider().paste(schematicFile, origin, false).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> plugin.sendMessage(player,
                        ok ? "hall.admin.paste-success" : "hall.admin.paste-failed")));
    }

    // ── addnpc ────────────────────────────────────────────────

    private void handleAddNpc(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "hall.admin.usage-addnpc");
            return;
        }
        String type = args[0].toUpperCase();
        String name = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : type + " NPC";
        plugin.getHallNPCManager().addNpcPoint(player, type, name);
    }

    // ── removenpc ─────────────────────────────────────────────

    private void handleRemoveNpc(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "hall.admin.usage-removenpc");
            return;
        }
        plugin.getHallNPCManager().removeNpcPoint(player, args[0]);
    }

    // ── listnpc ───────────────────────────────────────────────

    private void handleListNpc(Player player) {
        var npcs = plugin.getHallNPCManager().listNpcPoints();
        if (npcs.isEmpty()) {
            plugin.sendMessage(player, "hall.admin.npc-list-empty");
            return;
        }
        plugin.sendMessage(player, "hall.admin.npc-list-header");
        for (var npc : npcs) {
            plugin.sendRaw(player, "<gray> - <white>" + npc.key
                    + "<dark_gray>: <aqua>" + npc.type
                    + " <dark_gray>@ <gray>" + npc.world
                    + " " + String.format("%.0f,%.0f,%.0f", npc.x, npc.y, npc.z));
        }
    }

    // ── setcost ───────────────────────────────────────────────

    private void handleSetCost(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "hall.admin.usage-setcost");
            return;
        }
        try {
            long cost = Long.parseLong(args[0]);
            if (cost < 0) throw new NumberFormatException();
            plugin.getHallConfigManager().setPurchaseCost(cost);
            plugin.sendMessage(player, "hall.admin.cost-set",
                    "{cost}", plugin.getEconomyProvider().format(cost));
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "error.invalid-number");
        }
    }

    // ── grant ─────────────────────────────────────────────────

    private void handleGrant(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "hall.admin.usage-grant");
            return;
        }
        String clanQuery = String.join(" ", args);
        Clan clan = plugin.getClanManager().getClanByName(clanQuery);
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery);
            return;
        }
        plugin.getClanHallManager().grantAccess(clan.getId())
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player,
                                ok ? "hall.admin.grant-success" : "hall.admin.grant-failed",
                                "{clan}", clan.getName())));
    }

    // ── revoke ────────────────────────────────────────────────

    private void handleRevoke(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(player, "hall.admin.usage-revoke");
            return;
        }
        String clanQuery = String.join(" ", args);
        Clan clan = plugin.getClanManager().getClanByName(clanQuery);
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery);
            return;
        }
        plugin.getClanHallManager().revokeAccess(clan.getId())
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.sendMessage(player,
                                ok ? "hall.admin.revoke-success" : "hall.admin.revoke-failed",
                                "{clan}", clan.getName())));
    }

    // ── info ──────────────────────────────────────────────────

    private void handleInfo(Player player) {
        var cfg = plugin.getHallConfigManager();
        Location vaultLoc = cfg.getVaultBlockLocation();
        String vaultStr = vaultLoc != null
                ? String.format("%s [%d,%d,%d]", vaultLoc.getWorld().getName(),
                vaultLoc.getBlockX(), vaultLoc.getBlockY(), vaultLoc.getBlockZ())
                : plugin.getMessagesManager().get("hall.admin.info-not-set");

        plugin.sendMessage(player, "hall.admin.info-header");
        plugin.sendMessage(player, "hall.admin.info-enabled",    "{value}", String.valueOf(cfg.isEnabled()));
        plugin.sendMessage(player, "hall.admin.info-cost",       "{value}", plugin.getEconomyProvider().format(cfg.getPurchaseCost()));
        plugin.sendMessage(player, "hall.admin.info-mode",       "{value}", cfg.isPermanentMode() ? "PERMANENT" : "DURATION (" + cfg.getDurationDays() + " days)");
        plugin.sendMessage(player, "hall.admin.info-region",
                "{world}", cfg.getRegionWorld(),
                "{x1}", String.valueOf((int) cfg.getRegionMinX()),
                "{y1}", String.valueOf((int) cfg.getRegionMinY()),
                "{z1}", String.valueOf((int) cfg.getRegionMinZ()),
                "{x2}", String.valueOf((int) cfg.getRegionMaxX()),
                "{y2}", String.valueOf((int) cfg.getRegionMaxY()),
                "{z2}", String.valueOf((int) cfg.getRegionMaxZ()));
        plugin.sendMessage(player, "hall.admin.info-schematic",  "{value}", cfg.getSchematicFile());
        plugin.sendMessage(player, "hall.admin.info-vaultblock", "{value}", vaultStr);
        plugin.sendMessage(player, "hall.admin.info-clans",      "{value}", String.valueOf(plugin.getClanHallManager().getAccessClanIds().size()));
        plugin.sendMessage(player, "hall.admin.info-players",    "{value}", String.valueOf(plugin.getClanHallManager().getPlayersInsideHall().size()));
        plugin.sendMessage(player, "hall.admin.info-npcs",       "{value}", String.valueOf(plugin.getHallNPCManager().listNpcPoints().size()));
    }

    // ── reload ────────────────────────────────────────────────

    private void handleReload(Player player) {
        plugin.getHallConfigManager().reload();
        plugin.sendMessage(player, "hall.admin.reload-success");
    }

    // ── setvaultblock ─────────────────────────────────────────

    private void handleSetVaultBlock(Player player) {
        Location target = player.getTargetBlockExact(5).getLocation();
        if (target == null) {
            plugin.sendMessage(player, "hall.admin.setvaultblock-no-block");
            return;
        }
        plugin.getHallConfigManager().setVaultBlockLocation(target);
        plugin.sendMessage(player, "hall.admin.setvaultblock-success",
                "{x}", String.valueOf(target.getBlockX()),
                "{y}", String.valueOf(target.getBlockY()),
                "{z}", String.valueOf(target.getBlockZ()),
                "{world}", target.getWorld().getName());
    }

    // ── clearvaultblock ───────────────────────────────────────

    private void handleClearVaultBlock(Player player) {
        plugin.getHallConfigManager().clearVaultBlockLocation();
        plugin.sendMessage(player, "hall.admin.clearvaultblock-success");
    }

    // ── Help ──────────────────────────────────────────────────

    private void sendHelp(Player player) {
        plugin.sendMessage(player, "hall.admin.help-header");
        plugin.sendMessage(player, "hall.admin.help-setregion");
        plugin.sendMessage(player, "hall.admin.help-setschematic");
        plugin.sendMessage(player, "hall.admin.help-paste");
        plugin.sendMessage(player, "hall.admin.help-addnpc");
        plugin.sendMessage(player, "hall.admin.help-removenpc");
        plugin.sendMessage(player, "hall.admin.help-listnpc");
        plugin.sendMessage(player, "hall.admin.help-setcost");
        plugin.sendMessage(player, "hall.admin.help-grant");
        plugin.sendMessage(player, "hall.admin.help-revoke");
        plugin.sendMessage(player, "hall.admin.help-info");
        plugin.sendMessage(player, "hall.admin.help-reload");
        plugin.sendMessage(player, "hall.admin.help-setvaultblock");
        plugin.sendMessage(player, "hall.admin.help-clearvaultblock");
    }
}
