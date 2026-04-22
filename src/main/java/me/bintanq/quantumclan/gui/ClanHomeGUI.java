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
 * GUI that lists all clan homes.
 *
 * BUG FIX #2: All text from messages.yml.
 * BUG FIX #6: Failures (home not found, cooldown) no longer close the GUI.
 * BUG FIX #7: Back button support when opened from main menu.
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
    private final GUINavigation backAction;
    private Inventory inventory;

    private final Map<Integer, String> slotToHome = new ConcurrentHashMap<>();

    public ClanHomeGUI(QuantumClan plugin, Clan clan, int page, GUINavigation backAction) {
        this.plugin     = plugin;
        this.mm         = plugin.getMiniMessage();
        this.clan       = clan;
        this.page       = Math.max(0, page);
        this.backAction = backAction;
    }

    public Inventory build() {
        var gc = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, SIZE, mm.deserialize(gc.getClanHomeTitle()));

        fillBorder();
        placeHomes();
        setNavigation();

        // Info slot
        String infoName = gc.getClanHomeInfoName();
        int maxHomes = plugin.getConfigManager().getMaxHomes(clan.getLevel());
        List<Component> infoLore = List.of(
                mm.deserialize(msg.get("gui.home-gui-info-lore1")),
                mm.deserialize(msg.get("gui.home-gui-info-lore2")),
                mm.deserialize(msg.get("gui.home-gui-info-lore3",
                        "{current}", String.valueOf(clan.getHomeCount()),
                        "{max}", String.valueOf(maxHomes)))
        );
        inventory.setItem(gc.getClanHomeInfoSlot(), makeItem(gc.getClanHomeInfoMat(), infoName, infoLore));

        return inventory;
    }

    private void fillBorder() {
        var gc = plugin.getGuiConfigManager();
        ItemStack glass = makeItem(gc.getClanHomeFiller(), " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isHomeSlot(i) && i != gc.getClanHomeInfoSlot()
                    && i != gc.getClanHomeCloseSlot()
                    && i != gc.getClanHomePrevSlot()
                    && i != gc.getClanHomeNextSlot()) {
                inventory.setItem(i, glass);
            }
        }
    }

    private void placeHomes() {
        var msg = plugin.getMessagesManager();
        slotToHome.clear();
        List<Clan.ClanHome> homes = new ArrayList<>((List) clan.getHomes());
        int start = page * HOME_SLOTS.length;
        int end   = Math.min(start + HOME_SLOTS.length, homes.size());

        for (int i = start; i < end; i++) {
            Clan.ClanHome home = homes.get(i);
            int slotIndex = i - start;
            int slot      = HOME_SLOTS[slotIndex];

            List<Component> lore = List.of(
                    mm.deserialize(msg.get("gui.home-gui-world", "{world}", home.getWorld())),
                    mm.deserialize(msg.get("gui.home-gui-coords",
                            "{x}", String.format("%.1f", home.getX()),
                            "{y}", String.format("%.1f", home.getY()),
                            "{z}", String.format("%.1f", home.getZ()))),
                    Component.empty(),
                    mm.deserialize(msg.get("gui.home-gui-click-tp")),
                    mm.deserialize(msg.get("gui.home-gui-click-del"))
            );

            inventory.setItem(slot, makeItem(plugin.getGuiConfigManager().getClanHomeEntryMat(),
                    "<green>" + home.getName(), lore));
            slotToHome.put(slot, home.getName());
        }
    }

    private void setNavigation() {
        var gc = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();
        List<Clan.ClanHome> homes = new ArrayList<>((List) clan.getHomes());
        int totalPages = Math.max(1, (int) Math.ceil(homes.size() / (double) HOME_SLOTS.length));

        // BUG FIX #7: Back button if opened from menu, Close if opened directly
        String closeOrBackName = backAction != null ? msg.get("gui.back") : msg.get("gui.close");
        inventory.setItem(gc.getClanHomeCloseSlot(),
                makeItem(Material.BARRIER, closeOrBackName, Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(gc.getClanHomePrevSlot(),
                    makeItem(Material.ARROW, msg.get("gui.prev"),
                            List.of(mm.deserialize(msg.get("gui.page-info",
                                    "{current}", String.valueOf(page),
                                    "{total}", String.valueOf(totalPages))))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(gc.getClanHomeNextSlot(),
                    makeItem(Material.ARROW, msg.get("gui.next"),
                            List.of(mm.deserialize(msg.get("gui.page-info",
                                    "{current}", String.valueOf(page + 2),
                                    "{total}", String.valueOf(totalPages))))));
        }
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;

        try {
            var gc = plugin.getGuiConfigManager();

            if (slot == gc.getClanHomeCloseSlot()) {
                if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                else player.closeInventory();
                return;
            }

            if (slot == gc.getClanHomePrevSlot() && page > 0) {
                openPage(player, page - 1); return;
            }
            if (slot == gc.getClanHomeNextSlot()) {
                List<Clan.ClanHome> homes = new ArrayList<>((List) clan.getHomes());
                int totalPages = Math.max(1, (int) Math.ceil(homes.size() / (double) HOME_SLOTS.length));
                if (page < totalPages - 1) { openPage(player, page + 1); return; }
                else {
                    if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                    else player.closeInventory();
                    return;
                }
            }

            String homeName = slotToHome.get(slot);
            if (homeName == null) return;

            Clan latest = plugin.getClanManager().getClanById(clan.getId());
            if (latest == null) {
                if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                else player.closeInventory();
                return;
            }
            Clan.ClanHome home = latest.getHome(homeName);
            if (home == null) {
                // BUG FIX #6: Don't close GUI on failure — send message and stay
                plugin.sendMessage(player, "home.teleport-not-found", "{value}", homeName);
                openPage(player, page); // Refresh current page
                return;
            }

            if (click == ClickType.LEFT) {
                handleTeleport(player, home);
            } else if (click == ClickType.RIGHT) {
                handleDelete(player, latest, homeName);
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void handleTeleport(Player player, Clan.ClanHome home) {
        if (!player.hasPermission("quantumclan.bypass.cooldown")) {
            long lastTp = plugin.getBuffTracker().getLastHomeTeleport(player.getUniqueId());
            long cooldown = plugin.getConfigManager().getHomeTeleportCooldown();
            long elapsed  = (System.currentTimeMillis() - lastTp) / 1000;
            if (elapsed < cooldown) {
                // BUG FIX #6: Don't close GUI — just send the message
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
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-delete-home")) {
            plugin.sendMessage(player, "error.role-no-permission",
                    "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole());
            return; // BUG FIX #6: Stay in GUI
        }

        plugin.getClanManager().deleteHome(latest.getId(), homeName)
                .thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) {
                        plugin.sendMessage(player, "home.delete-success", "{value}", homeName);
                        openPage(player, page); // Refresh
                    } else {
                        plugin.sendMessage(player, "home.delete-not-found", "{value}", homeName);
                    }
                }));
    }

    private void openPage(Player player, int newPage) {
        player.closeInventory();
        Clan latest = plugin.getClanManager().getClanById(clan.getId());
        if (latest == null) { if (backAction != null) backAction.navigate(); return; }
        ClanHomeGUI gui = new ClanHomeGUI(plugin, latest, newPage, backAction);
        player.openInventory(gui.build());
    }

    /** Open directly from command (no back button). */
    public static void open(QuantumClan plugin, Player player, Clan clan) {
        player.openInventory(new ClanHomeGUI(plugin, clan, 0, null).build());
    }

    /** Open from parent menu (shows back button). */
    public static void openFromMenu(QuantumClan plugin, Player player, Clan clan, GUINavigation backAction) {
        player.openInventory(new ClanHomeGUI(plugin, clan, 0, backAction).build());
    }

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