package me.bintanq.quantumclan.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent builder for ItemStack with MiniMessage support.
 *
 * Usage:
 *   ItemStack item = ItemBuilder.of(Material.DIAMOND_SWORD)
 *       .name("<red>My Sword")
 *       .lore("<gray>A powerful sword.", "<gray>Use with care.")
 *       .enchant(Enchantment.SHARPNESS, 5)
 *       .hideFlags()
 *       .build();
 */
public class ItemBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ItemStack item;
    private final ItemMeta  meta;

    // ── Constructor ───────────────────────────────────────────

    private ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
        if (this.meta == null) {
            throw new IllegalArgumentException("Material " + material + " does not support ItemMeta.");
        }
    }

    // ── Factory methods ───────────────────────────────────────

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material, 1);
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, Math.max(1, amount));
    }

    // ── Name ──────────────────────────────────────────────────

    /**
     * Sets the display name using MiniMessage format.
     * Italic is disabled by default.
     */
    public ItemBuilder name(String miniMessage) {
        meta.displayName(MM.deserialize("<!italic>" + miniMessage));
        return this;
    }

    /**
     * Sets the display name as an Adventure Component directly.
     */
    public ItemBuilder name(Component component) {
        meta.displayName(component);
        return this;
    }

    // ── Lore ──────────────────────────────────────────────────

    /**
     * Sets lore lines using MiniMessage format.
     * Italic is disabled by default on each line.
     */
    public ItemBuilder lore(String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(MM.deserialize("<!italic>" + line));
        }
        meta.lore(lore);
        return this;
    }

    /**
     * Sets lore lines as Adventure Components.
     */
    public ItemBuilder lore(List<Component> lore) {
        meta.lore(lore);
        return this;
    }

    /**
     * Appends additional lore lines to any existing lore.
     */
    public ItemBuilder appendLore(String... lines) {
        List<Component> existing = meta.lore() != null
                ? new ArrayList<>(meta.lore())
                : new ArrayList<>();
        for (String line : lines) {
            existing.add(MM.deserialize("<!italic>" + line));
        }
        meta.lore(existing);
        return this;
    }

    // ── Amount ────────────────────────────────────────────────

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    // ── Enchantments ──────────────────────────────────────────

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    // ── Flags ─────────────────────────────────────────────────

    public ItemBuilder hideFlags(ItemFlag... flags) {
        if (flags.length == 0) {
            meta.addItemFlags(ItemFlag.values());
        } else {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Adds a glowing enchantment effect without showing any enchantment lore.
     */
    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    // ── Custom model data ─────────────────────────────────────

    public ItemBuilder customModelData(int data) {
        meta.setCustomModelData(data);
        return this;
    }

    // ── Unbreakable ───────────────────────────────────────────

    public ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        return this;
    }

    // ── Persistent Data Container ─────────────────────────────

    public <T, Z> ItemBuilder pdc(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    // ── Skull owner ───────────────────────────────────────────

    /**
     * Sets the skull owner for PLAYER_HEAD items.
     * No-op if this item is not a skull.
     */
    public ItemBuilder skullOwner(org.bukkit.OfflinePlayer owner) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(owner);
        }
        return this;
    }

    // ── Custom meta transform ─────────────────────────────────

    /**
     * Apply arbitrary meta modifications via a Consumer.
     */
    public ItemBuilder meta(Consumer<ItemMeta> consumer) {
        consumer.accept(meta);
        return this;
    }

    // ── Build ─────────────────────────────────────────────────

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    // ── Static helpers ────────────────────────────────────────

    /**
     * Creates a simple glass pane filler (used for GUI borders).
     */
    public static ItemStack filler(Material material) {
        return ItemBuilder.of(material).name(" ").build();
    }

    /**
     * Creates a close/back button.
     */
    public static ItemStack closeButton() {
        return ItemBuilder.of(Material.BARRIER)
                .name("<red>Tutup")
                .build();
    }

    /**
     * Creates a prev-page arrow button.
     */
    public static ItemStack prevButton(int currentPage, int totalPages) {
        return ItemBuilder.of(Material.ARROW)
                .name("<yellow>« Sebelumnya")
                .lore("<gray>Halaman " + currentPage + " / " + totalPages)
                .build();
    }

    /**
     * Creates a next-page arrow button.
     */
    public static ItemStack nextButton(int nextPage, int totalPages) {
        return ItemBuilder.of(Material.ARROW)
                .name("<yellow>Berikutnya »")
                .lore("<gray>Halaman " + nextPage + " / " + totalPages)
                .build();
    }
}