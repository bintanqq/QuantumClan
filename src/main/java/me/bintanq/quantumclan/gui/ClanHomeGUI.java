package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI that lists all clan sethomes.
 *
 * Layout (54 slots, up to 18 homes per page):
 *  Slots 10-16, 19-25, 28-34 — home entries
 *  Each home entry:
 *    - LEFT CLICK  → teleport to home (if player has can-set-home permission or is member)
 *    - RIGHT CLICK → delete home (if player has can-delete-home permission)
 *  Slot 49 — Close
 *  Slot 45 — Prev page
 *  Slot 53 — Next page
 */
public class ClanHomeGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int[] HOME_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final Clan clan;
    private final int page;
    private Inventory inventory;

    // slot → home name mapping for this page
    private final Map<Integer, String> slotToHome = new ConcurrentHashMap<>();

    public ClanHomeGUI(QuantumClan plugin, Clan clan, int page) {
        this.plugin = plugin;
        this.mm     = plugin.getMiniMessage();
        this.clan   = clan;
        this.page   = Math.max(0, page);
    }

    // ── Build ─────────────────────────────────────────────────

    public Inventory build() {
        inventory = Bukkit.createInventory(this, SIZE,
                mm.deserialize("<dark_gray>[ <green>Clan Homes <dark_gray>]"));

        fillBorder();
        placeHomes();
        setNavigation();

        // Info slot
        inventory.setItem(4, makeItem(Material.COMPASS,
                "<green>Clan Homes",
                List.of(
                        mm.deserialize("<gray>Klik kiri: <yellow>Teleport"),
                        mm.deserialize("<gray>Klik kanan: <red>Hapus home"),
                        mm.deserialize("<gray>Total: <yellow>" + clan.getHomeCount()
                                + " <dark_gray>/ <yellow>"
                                + plugin.getConfigManager().getMaxHomes(clan.getLevel()))
                )));

        return inventory;
    }

    private void fillBorder() {
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isHomeSlot(i) && i != 4 && i != 45 && i != 49 && i != 53) {
                inventory.setItem(i, glass);
            }
        }
    }

    private void placeHomes() {
        slotToHome.clear();
        List<Clan.ClanHome> homes = new ArrayList<>((List) clan.getHomes());
        int start = page * HOME_SLOTS.length;
        int end   = Math.min(start + HOME_SLOTS.length, homes.size());

        for (int i = start; i < end; i++) {
            Clan.ClanHome home = homes.get(i);
            int slotIndex = i - start;
            int slot      = HOME_SLOTS[slotIndex];

            List<Component> lore = List.of(
                    mm.deserialize("<gray>World: <yellow>" + home.getWorld()),
                    mm.deserialize("<gray>X: <yellow>" + String.format("%.1f", home.getX())
                            + " <gray>Y: <yellow>" + String.format("%.1f", home.getY())
                            + " <gray>Z: <yellow>" + String.format("%.1f", home.getZ())),
                    Component.empty(),
                    mm.deserialize("<green>» Klik kiri untuk teleport"),
                    mm.deserialize("<red>» Klik kanan untuk hapus")
            );

            inventory.setItem(slot, makeItem(Material.LODESTONE,
                    "<green>" + home.getName(), lore));
            slotToHome.put(slot, home.getName());
        }
    }

    private void setNavigation() {
        List<Clan.ClanHome> homes = new ArrayList<>((List) clan.getHomes());
        int totalPages = Math.max(1, (int) Math.ceil(homes.size() / (double) HOME_SLOTS.length));

        inventory.setItem(49, makeItem(Material.BARRIER, "<red>Tutup", Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(45, makeItem(Material.ARROW,
                    "<yellow>« Sebelumnya",
                    List.of(mm.deserialize("<gray>Halaman " + page + " / " + totalPages))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(53, makeItem(Material.ARROW,
                    "<yellow>Berikutnya »",
                    List.of(mm.deserialize("<gray>Halaman " + (page + 2) + " / " + totalPages))));
        }
    }

    // ── Click handler ─────────────────────────────────────────

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;

        try {
            if (slot == 49) { player.closeInventory(); return; }

            if (slot == 45 && page > 0) {
                openPage(player, page - 1); return;
            }
            if (slot == 53) {
                List<Clan.ClanHome> homes = new ArrayList<>((List) clan.getHomes());
                int totalPages = Math.max(1,
                        (int) Math.ceil(homes.size() / (double) HOME_SLOTS.length));
                if (page < totalPages - 1) { openPage(player, page + 1); return; }
                else { player.closeInventory(); return; }
            }

            String homeName = slotToHome.get(slot);
            if (homeName == null) return;

            // Refresh clan from cache
            Clan latest = plugin.getClanManager().getClanById(clan.getId());
            if (latest == null) { player.closeInventory(); return; }
            Clan.ClanHome home = latest.getHome(homeName);
            if (home == null) {
                plugin.sendMessage(player, "home.teleport-not-found", "{value}", homeName);
                openPage(player, page);
                return;
            }

            if (click == ClickType.LEFT) {
                // Teleport with cooldown check
                handleTeleport(player, home);
            } else if (click == ClickType.RIGHT) {
                // Delete — check permission
                handleDelete(player, latest, homeName);
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void handleTeleport(Player player, Clan.ClanHome home) {
        // Bypass cooldown check for ops
        if (!player.hasPermission("quantumclan.bypass.cooldown")) {
            long lastTp = plugin.getBuffTracker().getLastHomeTeleport(player.getUniqueId());
            long cooldown = plugin.getConfigManager().getHomeTeleportCooldown();
            long elapsed  = (System.currentTimeMillis() - lastTp) / 1000;
            if (elapsed < cooldown) {
                plugin.sendMessage(player, "home.teleport-cooldown",
                        "{value}", String.valueOf(cooldown - elapsed));
                return;
            }
        }

        Location loc = home.toBukkitLocation();
        if (loc == null) {
            plugin.sendMessage(player, "home.teleport-not-found", "{value}", home.getName());
            return;
        }

        player.closeInventory();
        player.teleport(loc);
        plugin.getBuffTracker().setLastHomeTeleport(player.getUniqueId());
        plugin.sendMessage(player, "home.teleport-success", "{value}", home.getName());
    }

    private void handleDelete(Player player, Clan latest, String homeName) {
        if (!plugin.getClanManager().hasRolePermission(
                player.getUniqueId(), "can-delete-home")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(
                            player.getUniqueId()).getRole());
            return;
        }

        plugin.getClanManager().deleteHome(latest.getId(), homeName)
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) {
                        plugin.sendMessage(player, "home.delete-success", "{value}", homeName);
                        openPage(player, page);
                    } else {
                        plugin.sendMessage(player, "home.delete-not-found", "{value}", homeName);
                    }
                }));
    }

    private void openPage(Player player, int newPage) {
        player.closeInventory();
        Clan latest = plugin.getClanManager().getClanById(clan.getId());
        if (latest == null) return;
        ClanHomeGUI gui = new ClanHomeGUI(plugin, latest, newPage);
        player.openInventory(gui.build());
    }

    // ── Static open helper ────────────────────────────────────

    public static void open(QuantumClan plugin, Player player, Clan clan) {
        ClanHomeGUI gui = new ClanHomeGUI(plugin, clan, 0);
        player.openInventory(gui.build());
    }

    // ── Helpers ───────────────────────────────────────────────

    private boolean isHomeSlot(int slot) {
        for (int s : HOME_SLOTS) if (s == slot) return true;
        return false;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    private ItemStack makeItem(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(mm.deserialize("<!italic>" + name));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}