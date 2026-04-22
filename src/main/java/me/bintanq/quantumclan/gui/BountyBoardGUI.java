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
 * Bounty Board GUI — Bug 7: back button support. All text from messages.yml.
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
    private final GUINavigation backAction;
    private Inventory inventory;

    public BountyBoardGUI(QuantumClan plugin, int page, GUINavigation backAction) {
        this.plugin     = plugin;
        this.mm         = plugin.getMiniMessage();
        this.page       = Math.max(0, page);
        this.bounties   = plugin.getBountyManager().getActiveBountiesSnapshot();
        this.backAction = backAction;
    }

    public Inventory build() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();

        inventory = Bukkit.createInventory(this, gc.getBountyBoardSize(),
                mm.deserialize(gc.getBountyBoardTitle()));

        fillBorder(gc.getBountyBoardFiller());
        setInfoItem();
        placeEntries();
        setNavigation();

        return inventory;
    }

    private void fillBorder(Material filler) {
        var gc = plugin.getGuiConfigManager();
        ItemStack glass = makeItem(filler, " ", Collections.emptyList());
        for (int i = 0; i < SIZE; i++) {
            if (!isEntrySlot(i) && i != gc.getBountyBoardInfoSlot()
                    && i != gc.getBountyBoardCloseSlot()
                    && i != gc.getBountyBoardPrevSlot()
                    && i != gc.getBountyBoardNextSlot()) {
                inventory.setItem(i, glass);
            }
        }
    }

    private void setInfoItem() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();
        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(msg.get("bounty.board-info-lore1",
                "{count}", String.valueOf(bounties.size()))));
        lore.add(mm.deserialize(msg.get("bounty.board-info-lore2")));
        lore.add(mm.deserialize(msg.get("bounty.board-info-lore3")));
        inventory.setItem(gc.getBountyBoardInfoSlot(),
                makeItem(Material.PLAYER_HEAD,
                        msg.get("gui.bounty-board-info-name"), lore));
    }

    private void placeEntries() {
        var msg = plugin.getMessagesManager();
        int start = page * ENTRY_SLOTS.length;
        int end   = Math.min(start + ENTRY_SLOTS.length, bounties.size());

        for (int i = start; i < end; i++) {
            BountyEntry entry = bounties.get(i);
            int slotIndex     = i - start;
            inventory.setItem(ENTRY_SLOTS[slotIndex], buildEntryItem(entry));
        }
    }

    private ItemStack buildEntryItem(BountyEntry entry) {
        var msg = plugin.getMessagesManager();
        OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getTargetUuid());
        String targetName    = target.getName() != null ? target.getName() : "Unknown";

        Clan posterClan = plugin.getClanManager().getClanById(entry.getClanIdPoster());
        String posterClanTag = posterClan != null ? posterClan.getColoredTag() : "?";

        long secondsLeft = entry.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        String timeLeft  = formatTimeLeft(secondsLeft);

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(msg.get("bounty.entry-target", "{name}", targetName)));
        lore.add(mm.deserialize(msg.get("bounty.entry-reward",
                "{amount}", plugin.getEconomyProvider().format(entry.getAmount()))));
        lore.add(mm.deserialize(msg.get("bounty.entry-posted-by", "{clan}", posterClanTag)));
        lore.add(mm.deserialize(msg.get("bounty.entry-expires", "{time}", timeLeft)));
        lore.add(Component.empty());
        lore.add(mm.deserialize(msg.get("bounty.entry-hint")));

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

    private void setNavigation() {
        var gc  = plugin.getGuiConfigManager();
        var msg = plugin.getMessagesManager();
        int totalPages = getTotalPages();

        String closeOrBack = backAction != null ? msg.get("gui.back") : msg.get("gui.close");
        inventory.setItem(gc.getBountyBoardCloseSlot(),
                makeItem(Material.BARRIER, closeOrBack, Collections.emptyList()));

        if (page > 0) {
            inventory.setItem(gc.getBountyBoardPrevSlot(),
                    makeItem(Material.ARROW, msg.get("gui.prev"),
                            List.of(mm.deserialize(msg.get("gui.page-info",
                                    "{current}", String.valueOf(page),
                                    "{total}", String.valueOf(totalPages))))));
        }
        if (page < totalPages - 1) {
            inventory.setItem(gc.getBountyBoardNextSlot(),
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
            if (slot == gc.getBountyBoardCloseSlot()) {
                if (backAction != null) { player.closeInventory(); backAction.navigate(); }
                else player.closeInventory();
                return;
            }
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
        player.openInventory(new BountyBoardGUI(plugin, newPage, backAction).build());
    }

    public static void open(QuantumClan plugin, Player player) {
        player.openInventory(new BountyBoardGUI(plugin, 0, null).build());
    }

    public static void openFromMenu(QuantumClan plugin, Player player, GUINavigation backAction) {
        player.openInventory(new BountyBoardGUI(plugin, 0, backAction).build());
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil(bounties.size() / (double) ENTRY_SLOTS.length));
    }

    private boolean isEntrySlot(int slot) {
        for (int s : ENTRY_SLOTS) if (s == slot) return true;
        return false;
    }

    private String formatTimeLeft(long seconds) {
        if (seconds <= 0) return plugin.getMessagesManager().get("bounty.expired");
        long hours = seconds / 3600;
        long mins  = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m " + (seconds % 60) + "s";
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