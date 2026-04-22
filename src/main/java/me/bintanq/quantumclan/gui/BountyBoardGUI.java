package me.bintanq.quantumclan.gui;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.BountyEntry;
import me.bintanq.quantumclan.model.Clan;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounty Board GUI — all messages from messages.yml and gui.yml.
 * No hardcoded strings.
 */
public class BountyBoardGUI implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    private final QuantumClan plugin;
    private final MiniMessage mm;
    private final int page;
    private final List<BountyEntry> bounties;
    private Inventory inventory;

    public BountyBoardGUI(QuantumClan plugin, int page) {
        this.plugin   = plugin;
        this.mm       = plugin.getMiniMessage();
        this.page     = Math.max(0, page);
        this.bounties = plugin.getBountyManager().getActiveBountiesSnapshot();
    }

    public Inventory build() {
        var gc = plugin.getGuiConfigManager();
        inventory = Bukkit.createInventory(this, gc.getBountyBoardSize(),
                mm.deserialize(gc.getBountyBoardTitle()));

        fillBorder(gc.getBountyBoardFiller());
        setInfoItem(gc);
        placeEntries();
        setNavigation(gc);

        return inventory;
    }

    private void fillBorder(Material filler) {
        ItemStack glass = makeItem(filler, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isEntrySlot(i) && i != gc().getBountyBoardInfoSlot()
                    && i != gc().getBountyBoardCloseSlot()
                    && i != gc().getBountyBoardPrevSlot()
                    && i != gc().getBountyBoardNextSlot()) {
                inventory.setItem(i, glass);
            }
        }
    }

    private void setInfoItem(me.bintanq.quantumclan.config.GuiConfigManager gc) {
        String infoName = gc.getBountyBoardInfoName();
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(plugin.getMessagesManager().get("bounty.board-info-lore1",
                "{count}", String.valueOf(bounties.size()))));
        lore.add(mm.deserialize(plugin.getMessagesManager().get("bounty.board-info-lore2")));
        lore.add(mm.deserialize(plugin.getMessagesManager().get("bounty.board-info-lore3")));
        inventory.setItem(gc.getBountyBoardInfoSlot(),
                makeItem(Material.PLAYER_HEAD, infoName, lore));
    }

    private void placeEntries() {
        int start = page * ENTRY_SLOTS.length;
        int end   = Math.min(start + ENTRY_SLOTS.length, bounties.size());

        for (int i = start; i < end; i++) {
            BountyEntry entry = bounties.get(i);
            int slotIndex     = i - start;
            inventory.setItem(ENTRY_SLOTS[slotIndex], buildEntryItem(entry));
        }
    }

    private ItemStack buildEntryItem(BountyEntry entry) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getTargetUuid());
        String targetName    = target.getName() != null ? target.getName() : "Unknown";

        Clan posterClan = plugin.getClanManager().getClanById(entry.getClanIdPoster());
        String posterClanTag = posterClan != null ? posterClan.getColoredTag()
                : plugin.getMessagesManager().get("error.unknown-subcommand");

        long secondsLeft = entry.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        String timeLeft  = formatTimeLeft(secondsLeft);

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(plugin.getMessagesManager().get("bounty.entry-target",
                "{name}", targetName)));
        lore.add(mm.deserialize(plugin.getMessagesManager().get("bounty.entry-reward",
                "{amount}", plugin.getEconomyProvider().format(entry.getAmount()))));
        lore.add(mm.deserialize(plugin.getMessagesManager().get("bounty.entry-posted-by",
                "{clan}", posterClanTag)));
        lore.add(mm.deserialize(plugin.getMessagesManager().get("bounty.entry-expires",
                "{time}", timeLeft)));
        lore.add(Component.empty());
        lore.add(mm.deserialize(plugin.getMessagesManager().get("bounty.entry-hint")));

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.displayName(mm.deserialize("<!italic><red>☠ <yellow>" + targetName));
            meta.lore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private void setNavigation(me.bintanq.quantumclan.config.GuiConfigManager gc) {
        int totalPages = getTotalPages();

        inventory.setItem(gc.getBountyBoardCloseSlot(),
                makeItem(Material.BARRIER,
                        plugin.getMessagesManager().get("gui.close"),
                        Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(gc.getBountyBoardPrevSlot(),
                    makeItem(Material.ARROW,
                            plugin.getMessagesManager().get("gui.prev"),
                            List.of(mm.deserialize(plugin.getMessagesManager().get("gui.page-info",
                                    "{current}", String.valueOf(page),
                                    "{total}", String.valueOf(totalPages))))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(gc.getBountyBoardNextSlot(),
                    makeItem(Material.ARROW,
                            plugin.getMessagesManager().get("gui.next"),
                            List.of(mm.deserialize(plugin.getMessagesManager().get("gui.page-info",
                                    "{current}", String.valueOf(page + 2),
                                    "{total}", String.valueOf(totalPages))))));
        }
    }

    public void handleClick(Player player, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();
        if (!processing.add(uuid)) return;
        try {
            var gc = gc();
            if (slot == gc.getBountyBoardCloseSlot()) { player.closeInventory(); return; }
            if (slot == gc.getBountyBoardPrevSlot() && page > 0) {
                openPage(player, page - 1); return;
            }
            if (slot == gc.getBountyBoardNextSlot() && page < getTotalPages() - 1) {
                openPage(player, page + 1); return;
            }
        } finally {
            processing.remove(uuid);
        }
    }

    private void openPage(Player player, int newPage) {
        player.closeInventory();
        player.openInventory(new BountyBoardGUI(plugin, newPage).build());
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new BountyBoardGUI(plugin, 0).build());
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil(bounties.size() / (double) ENTRY_SLOTS.length));
    }

    private boolean isEntrySlot(int slot) {
        for (int s : ENTRY_SLOTS) if (s == slot) return true;
        return false;
    }

    private String formatTimeLeft(long seconds) {
        if (seconds <= 0) return plugin.getMessagesManager().get("bounty.expired",
                "{player}", "");
        long hours = seconds / 3600;
        long mins  = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m " + (seconds % 60) + "s";
    }

    private me.bintanq.quantumclan.config.GuiConfigManager gc() {
        return plugin.getGuiConfigManager();
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