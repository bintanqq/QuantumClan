package me.bintanq.quantumclan.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Utility methods for MiniMessage parsing, stripping, and formatting.
 *
 * All methods are static. Uses a shared MiniMessage instance.
 */
public final class MiniMessageUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MiniMessageUtil() {}

    // ── Parse ─────────────────────────────────────────────────

    /**
     * Parses a MiniMessage string into a Component.
     */
    public static Component parse(String miniMessage) {
        if (miniMessage == null) return Component.empty();
        return MM.deserialize(miniMessage);
    }

    /**
     * Parses a MiniMessage string with italic disabled.
     * Convenient for item names and lore lines.
     */
    public static Component parseNoItalic(String miniMessage) {
        if (miniMessage == null) return Component.empty();
        return MM.deserialize("<!italic>" + miniMessage);
    }

    // ── Strip ─────────────────────────────────────────────────

    /**
     * Strips all MiniMessage tags from the input string and returns plain text.
     * Useful for console logging or comparison operations.
     */
    public static String strip(String miniMessage) {
        if (miniMessage == null) return "";
        return MM.stripTags(miniMessage);
    }

    /**
     * Converts an Adventure Component to plain text (no formatting).
     */
    public static String toPlain(Component component) {
        if (component == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // ── Escape ────────────────────────────────────────────────

    /**
     * Escapes all MiniMessage tags in the input string.
     * Use this to safely embed user-provided text inside MiniMessage strings
     * without executing any embedded tags.
     *
     * Example: escapeForMiniMessage("<red>hello") → "\<red\>hello"
     */
    public static String escape(String raw) {
        if (raw == null) return "";
        return MM.escapeTags(raw);
    }

    // ── Colour formatting shortcuts ───────────────────────────

    /**
     * Wraps the given text in a MiniMessage color tag.
     *
     * Example: color("Hello", "gold") → "<gold>Hello</gold>"
     */
    public static String color(String text, String colorTag) {
        if (text == null) return "";
        return "<" + colorTag + ">" + text + "</" + colorTag + ">";
    }

    /**
     * Formats a number with commas for readability.
     * Example: 1000000 → "1,000,000"
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Formats a double amount with 2 decimal places.
     */
    public static String formatDouble(double amount) {
        return String.format("%,.2f", amount);
    }

    // ── Centering ─────────────────────────────────────────────

    /**
     * Pads a plain string with spaces to center it in a fixed-width display
     * (useful for book/sign text — not chat, which is proportional font).
     *
     * @param text  Plain text (no formatting codes)
     * @param width Total character width to center within
     */
    public static String center(String text, int width) {
        if (text == null || text.isEmpty()) return " ".repeat(width);
        int padding = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(padding) + text;
    }

    // ── Boolean display ───────────────────────────────────────

    /**
     * Returns a MiniMessage-formatted "✔ Ya" or "✘ Tidak" based on the boolean.
     */
    public static String booleanDisplay(boolean value) {
        return value ? "<green>✔ Ya" : "<red>✘ Tidak";
    }

    /**
     * Returns a MiniMessage-formatted active/inactive status string.
     */
    public static String activeDisplay(boolean active) {
        return active ? "<green>Aktif" : "<red>Tidak Aktif";
    }

    // ── Shared instance ───────────────────────────────────────

    /**
     * Returns the shared MiniMessage instance.
     * Use this instead of creating a new instance each time.
     */
    public static MiniMessage get() {
        return MM;
    }
}