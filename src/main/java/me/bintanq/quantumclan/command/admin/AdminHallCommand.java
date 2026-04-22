package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Arrays;

/**
 * Handles /qclanadmin hall <subcommand>
 *
 * Subcommands:
 *   setregion               → two-step corner selection
 *   setschematic <file>     → select schematic file
 *   paste                   → paste schematic at defined origin
 *   addnpc <type> [name]    → spawn NPC at player location + save to config
 *   removenpc <type>        → remove NPC point
 *   listnpc                 → list all NPC points
 *   setcost <amount>        → update purchase cost
 *   grant <clan>            → grant permanent access
 *   revoke <clan>           → revoke access
 *   info                    → show hall status
 *   reload                  → reload halls.yml
 */
public class AdminHallCommand {

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
            case "setregion"    -> handleSetRegion(player);
            case "setschematic" -> handleSetSchematic(player, Arrays.copyOfRange(args, 1, args.length));
            case "paste"        -> handlePaste(player);
            case "addnpc"       -> handleAddNpc(player, Arrays.copyOfRange(args, 1, args.length));
            case "removenpc"    -> handleRemoveNpc(player, Arrays.copyOfRange(args, 1, args.length));
            case "listnpc"      -> handleListNpc(player);
            case "setcost"      -> handleSetCost(player, Arrays.copyOfRange(args, 1, args.length));
            case "grant"        -> handleGrant(player, Arrays.copyOfRange(args, 1, args.length));
            case "revoke"       -> handleRevoke(player, Arrays.copyOfRange(args, 1, args.length));
            case "info"         -> handleInfo(player);
            case "reload"       -> handleReload(player);
            default             -> sendHelp(player);
        }
    }

    // ── setregion ─────────────────────────────────────────────

    private void handleSetRegion(Player player) {
        if (!plugin.getClanHallManager().hasPendingCorner1(player.getUniqueId())) {
            // First call: set corner 1
            plugin.getClanHallManager().setPendingCorner1(player.getUniqueId(), player.getLocation());
            plugin.sendMessage(player, "hall.admin.setregion-corner1",
                    "{x}", String.valueOf(player.getLocation().getBlockX()),
                    "{y}", String.valueOf(player.getLocation().getBlockY()),
                    "{z}", String.valueOf(player.getLocation().getBlockZ()));
        } else {
            // Second call: set corner 2
            Location corner1 = plugin.getClanHallManager().consumePendingCorner1(player.getUniqueId());
            Location corner2 = player.getLocation();

            // Ensure min/max ordering
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

            plugin.sendMessage(player, "hall.admin.setregion-done",
                    "{x1}", String.valueOf((int)minX), "{y1}", String.valueOf((int)minY), "{z1}", String.valueOf((int)minZ),
                    "{x2}", String.valueOf((int)maxX), "{y2}", String.valueOf((int)maxY), "{z2}", String.valueOf((int)maxZ));
        }
    }

    // ── setschematic ──────────────────────────────────────────

    private void handleSetSchematic(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendRaw(player, "<red>/qclanadmin hall setschematic <filename>");
            return;
        }
        String filename = args[0];
        File schematicDir = new File(plugin.getDataFolder(), "halls/schematics");
        File schematicFile = new File(schematicDir, filename);
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
        File schematicDir = new File(plugin.getDataFolder(), "halls/schematics");
        File schematicFile = new File(schematicDir, filename);

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

        // Auto-select provider based on file extension
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        var provider = plugin.getSchematicProvider();

        provider.paste(schematicFile, origin, false).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.sendMessage(player, "hall.admin.paste-success");
                    else    plugin.sendMessage(player, "hall.admin.paste-failed");
                }));
    }

    // ── addnpc ────────────────────────────────────────────────

    private void handleAddNpc(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendRaw(player, "<red>/qclanadmin hall addnpc <TYPE> [name]");
            plugin.sendRaw(player, "<gray>Types: CLAN_SHOP, CONTRIBUTION_SHOP, COINS_SHOP, WAR_REGISTER, CLAN_INFO, UPGRADE, HALL_INFO");
            return;
        }
        String type = args[0].toUpperCase();
        String name = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : type + " NPC";

        plugin.getHallNPCManager().addNpcPoint(player, type, name);
    }

    // ── removenpc ─────────────────────────────────────────────

    private void handleRemoveNpc(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendRaw(player, "<red>/qclanadmin hall removenpc <type>");
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
            plugin.sendRaw(player, "<red>/qclanadmin hall setcost <amount>");
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
            plugin.sendRaw(player, "<red>/qclanadmin hall grant <clan>");
            return;
        }
        String clanQuery = String.join(" ", args);
        Clan clan = plugin.getClanManager().getClanByName(clanQuery);
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery);
            return;
        }
        plugin.getClanHallManager().grantAccess(clan.getId())
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.sendMessage(player, "hall.admin.grant-success", "{clan}", clan.getName());
                    else    plugin.sendMessage(player, "hall.admin.grant-failed",  "{clan}", clan.getName());
                }));
    }

    // ── revoke ────────────────────────────────────────────────

    private void handleRevoke(Player player, String[] args) {
        if (args.length == 0) {
            plugin.sendRaw(player, "<red>/qclanadmin hall revoke <clan>");
            return;
        }
        String clanQuery = String.join(" ", args);
        Clan clan = plugin.getClanManager().getClanByName(clanQuery);
        if (clan == null) {
            plugin.sendMessage(player, "clan.not-found", "{clan}", clanQuery);
            return;
        }
        plugin.getClanHallManager().revokeAccess(clan.getId())
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) plugin.sendMessage(player, "hall.admin.revoke-success", "{clan}", clan.getName());
                    else    plugin.sendMessage(player, "hall.admin.revoke-failed",  "{clan}", clan.getName());
                }));
    }

    // ── info ──────────────────────────────────────────────────

    private void handleInfo(Player player) {
        var cfg = plugin.getHallConfigManager();
        plugin.sendRaw(player, "<dark_gray>━━━ <gold>Clan Hall Info <dark_gray>━━━");
        plugin.sendRaw(player, "<gray>Enabled: <white>" + cfg.isEnabled());
        plugin.sendRaw(player, "<gray>Cost: <white>" + plugin.getEconomyProvider().format(cfg.getPurchaseCost()));
        plugin.sendRaw(player, "<gray>Mode: <white>" + (cfg.isPermanentMode() ? "PERMANENT" : "DURATION (" + cfg.getDurationDays() + " days)"));
        plugin.sendRaw(player, "<gray>Region: <white>" + cfg.getRegionWorld()
                + " [" + (int)cfg.getRegionMinX() + "," + (int)cfg.getRegionMinY() + "," + (int)cfg.getRegionMinZ()
                + "] → [" + (int)cfg.getRegionMaxX() + "," + (int)cfg.getRegionMaxY() + "," + (int)cfg.getRegionMaxZ() + "]");
        plugin.sendRaw(player, "<gray>Schematic: <white>" + cfg.getSchematicFile());
        plugin.sendRaw(player, "<gray>Clans with access: <white>" + plugin.getClanHallManager().getAccessClanIds().size());
        plugin.sendRaw(player, "<gray>Players inside: <white>" + plugin.getClanHallManager().getPlayersInsideHall().size());
        plugin.sendRaw(player, "<gray>NPC Points: <white>" + plugin.getHallNPCManager().listNpcPoints().size());
        plugin.sendRaw(player, "<gray>Discounts: <white>" + cfg.isDiscountsEnabled()
                + " (shop=" + cfg.getDiscountPercent("clan-shop") + "%, contrib="
                + cfg.getDiscountPercent("contribution-shop") + "%, coins="
                + cfg.getDiscountPercent("coins-shop") + "%, upgrade="
                + cfg.getDiscountPercent("upgrade") + "%)");
    }

    // ── reload ────────────────────────────────────────────────

    private void handleReload(Player player) {
        plugin.getHallConfigManager().reload();
        plugin.sendMessage(player, "hall.admin.reload-success");
    }

    // ── Help ──────────────────────────────────────────────────

    private void sendHelp(Player player) {
        plugin.sendRaw(player, "<dark_gray>━━ <gold>/qclanadmin hall <dark_gray>━━");
        plugin.sendRaw(player, "<gold>setregion <dark_gray>- <gray>Set hall region (2-step)");
        plugin.sendRaw(player, "<gold>setschematic <file> <dark_gray>- <gray>Select schematic");
        plugin.sendRaw(player, "<gold>paste <dark_gray>- <gray>Paste schematic at origin");
        plugin.sendRaw(player, "<gold>addnpc <type> [name] <dark_gray>- <gray>Add NPC at your location");
        plugin.sendRaw(player, "<gold>removenpc <type> <dark_gray>- <gray>Remove NPC point");
        plugin.sendRaw(player, "<gold>listnpc <dark_gray>- <gray>List all NPC points");
        plugin.sendRaw(player, "<gold>setcost <amount> <dark_gray>- <gray>Set purchase cost");
        plugin.sendRaw(player, "<gold>grant <clan> <dark_gray>- <gray>Grant hall access");
        plugin.sendRaw(player, "<gold>revoke <clan> <dark_gray>- <gray>Revoke hall access");
        plugin.sendRaw(player, "<gold>info <dark_gray>- <gray>Show hall status");
        plugin.sendRaw(player, "<gold>reload <dark_gray>- <gray>Reload halls.yml");
    }
}