package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads clan shop, contribution shop, and coins shop items from shop.yml.
 */
public class ShopConfigManager {

    private final QuantumClan plugin;
    private final List<ShopItem>    clanShopItems         = new ArrayList<>();
    private final List<ContribItem> contributionShopItems = new ArrayList<>();
    private final List<CoinsItem>   coinsShopItems        = new ArrayList<>();

    public ShopConfigManager(QuantumClan plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        clanShopItems.clear();
        contributionShopItems.clear();
        coinsShopItems.clear();

        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // ── Clan shop ──────────────────────────────────────────
        List<?> shopList = cfg.getList("clan-shop");
        if (shopList != null) {
            for (Object obj : shopList) {
                if (!(obj instanceof Map<?, ?> m)) continue;
                try { clanShopItems.add(parseShopItem(m)); }
                catch (Exception e) { plugin.getLogger().warning("[ShopConfig] Failed to parse shop item: " + e.getMessage()); }
            }
        }

        // ── Contribution shop ──────────────────────────────────
        List<?> contribList = cfg.getList("contribution-shop");
        if (contribList != null) {
            for (Object obj : contribList) {
                if (!(obj instanceof Map<?, ?> m)) continue;
                try { contributionShopItems.add(parseContribItem(m)); }
                catch (Exception e) { plugin.getLogger().warning("[ShopConfig] Failed to parse contrib item: " + e.getMessage()); }
            }
        }

        // ── Coins shop ─────────────────────────────────────────
        List<?> coinsList = cfg.getList("coins-shop");
        if (coinsList != null) {
            for (Object obj : coinsList) {
                if (!(obj instanceof Map<?, ?> m)) continue;
                try { coinsShopItems.add(parseCoinsItem(m)); }
                catch (Exception e) { plugin.getLogger().warning("[ShopConfig] Failed to parse coins item: " + e.getMessage()); }
            }
        }

        plugin.getLogger().info("[ShopConfig] Loaded " + clanShopItems.size()
                + " clan, " + contributionShopItems.size()
                + " contrib, " + coinsShopItems.size() + " coins items.");
    }

    // ── Parsers ───────────────────────────────────────────────

    private ShopItem parseShopItem(Map<?, ?> m) {
        String id       = str(m, "id", "unknown");
        ShopItem.Type t = ShopItem.Type.valueOf(str(m, "type", "CONSUMABLE").toUpperCase());
        String name     = str(m, "name", "Item");
        List<String> lore = strList(m, "lore");
        Material mat    = parseMaterial(str(m, "material", "PAPER"));
        long price      = toLong(m.get("price"), 0L);
        String effect   = str(m, "effect", "");
        int amplifier   = toInt(m.get("amplifier"), 0);
        int duration    = toInt(m.get("duration"), 0);
        boolean confirm = Boolean.parseBoolean(str(m, "confirm", "false"));
        return new ShopItem(id, t, name, lore, mat, price, effect, amplifier, duration, confirm);
    }

    private ContribItem parseContribItem(Map<?, ?> m) {
        String id         = str(m, "id", "unknown");
        ContribItem.Type t = ContribItem.Type.valueOf(str(m, "type", "ITEM").toUpperCase());
        String name       = str(m, "name", "Item");
        List<String> lore = strList(m, "lore");
        Material mat      = parseMaterial(str(m, "material", "PAPER"));
        int costPoints    = toInt(m.get("cost-points"), 10);
        String effect     = str(m, "effect", "");
        int amplifier     = toInt(m.get("amplifier"), 0);
        int duration      = toInt(m.get("duration"), 0);
        int amount        = toInt(m.get("amount"), 1);
        return new ContribItem(id, t, name, lore, mat, costPoints, effect, amplifier, duration, amount);
    }

    private CoinsItem parseCoinsItem(Map<?, ?> m) {
        String id         = str(m, "id", "unknown");
        CoinsItem.Type t  = CoinsItem.Type.valueOf(str(m, "type", "ITEM").toUpperCase());
        String name       = str(m, "name", "Item");
        List<String> lore = strList(m, "lore");
        Material mat      = parseMaterial(str(m, "material", "PAPER"));
        long costCoins    = toLong(m.get("cost-coins"), 10L);
        String effect     = str(m, "effect", "");
        int amplifier     = toInt(m.get("amplifier"), 0);
        int duration      = toInt(m.get("duration"), 0);
        int amount        = toInt(m.get("amount"), 1);
        String action     = str(m, "action", "");
        String actionValue = str(m, "action-value", "");
        return new CoinsItem(id, t, name, lore, mat, costCoins, effect, amplifier, duration, amount, action, actionValue);
    }

    // ── Getters ───────────────────────────────────────────────

    public List<ShopItem>    getClanShopItems()          { return List.copyOf(clanShopItems); }
    public List<ContribItem> getContributionShopItems()  { return List.copyOf(contributionShopItems); }
    public List<CoinsItem>   getCoinsShopItems()         { return List.copyOf(coinsShopItems); }

    public ShopItem    getShopItemById(String id)   { return clanShopItems.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null); }
    public ContribItem getContribItemById(String id){ return contributionShopItems.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null); }
    public CoinsItem   getCoinsItemById(String id)  { return coinsShopItems.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null); }

    // ── Nested: ShopItem ──────────────────────────────────────

    public static class ShopItem {
        public enum Type { BUFF, CONSUMABLE, UTILITY }
        private final String id; private final Type type; private final String name;
        private final List<String> lore; private final Material material;
        private final long price; private final String effect;
        private final int amplifier; private final int duration; private final boolean confirm;

        public ShopItem(String id, Type type, String name, List<String> lore, Material material,
                        long price, String effect, int amplifier, int duration, boolean confirm) {
            this.id=id; this.type=type; this.name=name; this.lore=lore; this.material=material;
            this.price=price; this.effect=effect; this.amplifier=amplifier;
            this.duration=duration; this.confirm=confirm;
        }
        public String getId()        { return id; }
        public Type   getType()      { return type; }
        public String getName()      { return name; }
        public List<String> getLore(){ return lore; }
        public Material getMaterial(){ return material; }
        public long   getPrice()     { return price; }
        public String getEffect()    { return effect; }
        public int    getAmplifier() { return amplifier; }
        public int    getDuration()  { return duration; }
        public boolean isConfirm()   { return confirm; }
    }

    // ── Nested: ContribItem ───────────────────────────────────

    public static class ContribItem {
        public enum Type { BUFF_PERSONAL, ITEM }
        private final String id; private final Type type; private final String name;
        private final List<String> lore; private final Material material;
        private final int costPoints; private final String effect;
        private final int amplifier; private final int duration; private final int amount;

        public ContribItem(String id, Type type, String name, List<String> lore, Material material,
                           int costPoints, String effect, int amplifier, int duration, int amount) {
            this.id=id; this.type=type; this.name=name; this.lore=lore; this.material=material;
            this.costPoints=costPoints; this.effect=effect; this.amplifier=amplifier;
            this.duration=duration; this.amount=amount;
        }
        public String getId()         { return id; }
        public Type   getType()       { return type; }
        public String getName()       { return name; }
        public List<String> getLore() { return lore; }
        public Material getMaterial() { return material; }
        public int    getCostPoints() { return costPoints; }
        public String getEffect()     { return effect; }
        public int    getAmplifier()  { return amplifier; }
        public int    getDuration()   { return duration; }
        public int    getAmount()     { return amount; }
    }

    // ── Nested: CoinsItem ─────────────────────────────────────

    public static class CoinsItem {
        public enum Type { BUFF_PERSONAL, ITEM }
        private final String id; private final Type type; private final String name;
        private final List<String> lore; private final Material material;
        private final long costCoins; private final String effect;
        private final int amplifier; private final int duration;
        private final int amount; private final String action; private final String actionValue;

        public CoinsItem(String id, Type type, String name, List<String> lore, Material material,
                         long costCoins, String effect, int amplifier, int duration,
                         int amount, String action, String actionValue) {
            this.id=id; this.type=type; this.name=name; this.lore=lore; this.material=material;
            this.costCoins=costCoins; this.effect=effect; this.amplifier=amplifier;
            this.duration=duration; this.amount=amount; this.action=action; this.actionValue=actionValue;
        }
        public String getId()         { return id; }
        public Type   getType()       { return type; }
        public String getName()       { return name; }
        public List<String> getLore() { return lore; }
        public Material getMaterial() { return material; }
        public long   getCostCoins()  { return costCoins; }
        public String getEffect()     { return effect; }
        public int    getAmplifier()  { return amplifier; }
        public int    getDuration()   { return duration; }
        public int    getAmount()     { return amount; }
        public String getAction()     { return action; }
        public String getActionValue(){ return actionValue; }
    }

    // ── Helpers ───────────────────────────────────────────────

    private String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key); return v != null ? String.valueOf(v) : def;
    }

    @SuppressWarnings("unchecked")
    private List<String> strList(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof List<?> list) {
            List<String> r = new ArrayList<>();
            for (Object o : list) r.add(String.valueOf(o));
            return r;
        }
        return new ArrayList<>();
    }

    private Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); } catch (Exception e) { return Material.PAPER; }
    }

    private int toInt(Object obj, int def) {
        if (obj instanceof Number n) return n.intValue();
        if (obj instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
        return def;
    }

    private long toLong(Object obj, long def) {
        if (obj instanceof Number n) return n.longValue();
        if (obj instanceof String s) { try { return Long.parseLong(s); } catch (Exception e) { return def; } }
        return def;
    }
}