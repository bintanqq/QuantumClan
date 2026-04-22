package me.bintanq.quantumclan.config;

import me.bintanq.quantumclan.QuantumClan;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

/**
 * Loads and exposes all GUI configuration from gui.yml.
 * Every GUI title, material, and item name is read from here — no hardcoding.
 */
public class GuiConfigManager {

    private final QuantumClan plugin;
    private FileConfiguration cfg;

    public GuiConfigManager(QuantumClan plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) plugin.saveResource("gui.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    // ── Generic accessors ─────────────────────────────────────

    public String getString(String path, String def) {
        String v = cfg.getString(path);
        return v != null ? v : def;
    }

    public int getInt(String path, int def) {
        return cfg.getInt(path, def);
    }

    public List<String> getStringList(String path) {
        return cfg.getStringList(path);
    }

    public Material getMaterial(String path, Material def) {
        String raw = cfg.getString(path);
        if (raw == null) return def;
        try { return Material.valueOf(raw.toUpperCase()); }
        catch (IllegalArgumentException e) { return def; }
    }

    public ConfigurationSection getSection(String path) {
        return cfg.getConfigurationSection(path);
    }

    // ── Main Menu ─────────────────────────────────────────────

    public String  getMainMenuTitle()    { return getString("main-menu.title", "<dark_gray>[ <aqua>Clan Menu <dark_gray>]"); }
    public int     getMainMenuSize()     { return getInt("main-menu.size", 54); }
    public Material getMainMenuFiller() { return getMaterial("main-menu.filler", Material.CYAN_STAINED_GLASS_PANE); }

    public int     getMainMenuSlot(String item)         { return getInt("main-menu.items." + item + ".slot", 0); }
    public Material getMainMenuMaterial(String item)    { return getMaterial("main-menu.items." + item + ".material", Material.PAPER); }
    public String  getMainMenuItemName(String item)     { return getString("main-menu.items." + item + ".name", item); }
    public List<String> getMainMenuItemLore(String item){ return getStringList("main-menu.items." + item + ".lore"); }

    // ── Clan Info ─────────────────────────────────────────────

    public String  getClanInfoTitle()   { return getString("clan-info.title", "<dark_gray>[ <aqua>{clan} <dark_gray>]"); }
    public int     getClanInfoSize()    { return getInt("clan-info.size", 54); }
    public Material getClanInfoFiller() { return getMaterial("clan-info.filler", Material.GRAY_STAINED_GLASS_PANE); }
    public int     getClanInfoSlot(String item)      { return getInt("clan-info." + item + ".slot", 0); }
    public Material getClanInfoMaterial(String item) { return getMaterial("clan-info." + item + ".material", Material.PAPER); }
    public String  getClanInfoName(String item)      { return getString("clan-info." + item + ".name", item); }

    // ── Clan Shop ─────────────────────────────────────────────

    public String  getClanShopTitle()   { return getString("clan-shop.title", "<dark_gray>[ <gold>Clan Shop <dark_gray>]"); }
    public int     getClanShopSize()    { return getInt("clan-shop.size", 54); }
    public Material getClanShopFiller() { return getMaterial("clan-shop.filler", Material.GRAY_STAINED_GLASS_PANE); }
    public int     getClanShopCloseSlot()    { return getInt("clan-shop.close.slot", 49); }
    public Material getClanShopCloseMat()    { return getMaterial("clan-shop.close.material", Material.BARRIER); }
    public String  getClanShopCloseName()    { return getString("clan-shop.close.name", "<red>Close"); }
    public int     getClanShopPrevSlot()     { return getInt("clan-shop.prev.slot", 45); }
    public int     getClanShopNextSlot()     { return getInt("clan-shop.next.slot", 53); }
    public int     getClanShopTreasurySlot() { return getInt("clan-shop.treasury-display.slot", 4); }
    public Material getClanShopTreasuryMat() { return getMaterial("clan-shop.treasury-display.material", Material.GOLD_INGOT); }
    public String  getClanShopTreasuryName() { return getString("clan-shop.treasury-display.name", "<gold>Clan Treasury: <yellow>{money}"); }

    // ── Shop Confirm ──────────────────────────────────────────

    public String  getShopConfirmTitle()   { return getString("shop-confirm.title", "<dark_gray>[ <gold>Confirm Purchase <dark_gray>]"); }
    public int     getShopConfirmSize()    { return getInt("shop-confirm.size", 27); }
    public Material getShopConfirmFiller() { return getMaterial("shop-confirm.filler", Material.GRAY_STAINED_GLASS_PANE); }
    public int     getShopConfirmYesSlot() { return getInt("shop-confirm.yes.slot", 11); }
    public Material getShopConfirmYesMat() { return getMaterial("shop-confirm.yes.material", Material.LIME_WOOL); }
    public String  getShopConfirmYesName() { return getString("shop-confirm.yes.name", "<green><bold>✔ CONFIRM"); }
    public int     getShopConfirmPreviewSlot() { return getInt("shop-confirm.preview.slot", 13); }
    public int     getShopConfirmNoSlot() { return getInt("shop-confirm.no.slot", 15); }
    public Material getShopConfirmNoMat() { return getMaterial("shop-confirm.no.material", Material.RED_WOOL); }
    public String  getShopConfirmNoName() { return getString("shop-confirm.no.name", "<red><bold>✘ CANCEL"); }

    // ── Clan Home ─────────────────────────────────────────────

    public String  getClanHomeTitle()     { return getString("clan-home.title", "<dark_gray>[ <green>Clan Homes <dark_gray>]"); }
    public int     getClanHomeSize()      { return getInt("clan-home.size", 54); }
    public Material getClanHomeFiller()   { return getMaterial("clan-home.filler", Material.GRAY_STAINED_GLASS_PANE); }
    public Material getClanHomeEntryMat() { return getMaterial("clan-home.home-material", Material.LODESTONE); }
    public int     getClanHomeInfoSlot()  { return getInt("clan-home.info.slot", 4); }
    public Material getClanHomeInfoMat()  { return getMaterial("clan-home.info.material", Material.COMPASS); }
    public String  getClanHomeInfoName()  { return getString("clan-home.info.name", "<green>Clan Homes"); }
    public int     getClanHomeCloseSlot() { return getInt("clan-home.close.slot", 49); }
    public int     getClanHomePrevSlot()  { return getInt("clan-home.prev.slot", 45); }
    public int     getClanHomeNextSlot()  { return getInt("clan-home.next.slot", 53); }

    // ── Bounty Board ──────────────────────────────────────────

    public String  getBountyBoardTitle()   { return getString("bounty-board.title", "<dark_gray>[ <red>☠ Bounty Board <dark_gray>]"); }
    public int     getBountyBoardSize()    { return getInt("bounty-board.size", 54); }
    public Material getBountyBoardFiller() { return getMaterial("bounty-board.filler", Material.RED_STAINED_GLASS_PANE); }
    public int     getBountyBoardInfoSlot() { return getInt("bounty-board.info.slot", 4); }
    public String  getBountyBoardInfoName() { return getString("bounty-board.info.name", "<red>☠ Bounty Board"); }
    public int     getBountyBoardCloseSlot(){ return getInt("bounty-board.close.slot", 49); }
    public int     getBountyBoardPrevSlot() { return getInt("bounty-board.prev.slot", 45); }
    public int     getBountyBoardNextSlot() { return getInt("bounty-board.next.slot", 53); }

    // ── Clan Top ──────────────────────────────────────────────

    public String  getClanTopTitle()   { return getString("clan-top.title", "<dark_gray>[ <gold>⭐ Top Clans <dark_gray>]"); }
    public int     getClanTopSize()    { return getInt("clan-top.size", 54); }
    public Material getClanTopFiller() { return getMaterial("clan-top.filler", Material.YELLOW_STAINED_GLASS_PANE); }
    public int     getClanTopInfoSlot() { return getInt("clan-top.info.slot", 4); }
    public String  getClanTopInfoName() { return getString("clan-top.info.name", "<gold>⭐ Clan Leaderboard"); }
    public int     getClanTopCloseSlot(){ return getInt("clan-top.close.slot", 49); }
    public int     getClanTopPrevSlot() { return getInt("clan-top.prev.slot", 45); }
    public int     getClanTopNextSlot() { return getInt("clan-top.next.slot", 53); }
    public Material getClanTopRank1Mat() { return getMaterial("clan-top.rank-1-material", Material.GOLD_BLOCK); }
    public Material getClanTopRank2Mat() { return getMaterial("clan-top.rank-2-material", Material.IRON_BLOCK); }
    public Material getClanTopRank3Mat() { return getMaterial("clan-top.rank-3-material", Material.COPPER_BLOCK); }
    public Material getClanTopDefaultMat(){ return getMaterial("clan-top.default-material", Material.STONE); }

    // ── Upgrade ───────────────────────────────────────────────

    public String  getUpgradeTitle()   { return getString("upgrade.title", "<dark_gray>[ <aqua>Upgrade Clan <dark_gray>]"); }
    public int     getUpgradeSize()    { return getInt("upgrade.size", 27); }
    public Material getUpgradeFiller() { return getMaterial("upgrade.filler", Material.GRAY_STAINED_GLASS_PANE); }
    public int     getUpgradeCurrentSlot()     { return getInt("upgrade.current.slot", 11); }
    public Material getUpgradeCurrentMat()     { return getMaterial("upgrade.current.material", Material.EXPERIENCE_BOTTLE); }
    public String  getUpgradeCurrentName()     { return getString("upgrade.current.name", "<aqua>Current Level: <yellow>{level}"); }
    public int     getUpgradeActionSlot()      { return getInt("upgrade.upgrade-can.slot", 13); }
    public Material getUpgradeCanMat()         { return getMaterial("upgrade.upgrade-can.material", Material.LIME_WOOL); }
    public String  getUpgradeCanName()         { return getString("upgrade.upgrade-can.name", "<green>✔ UPGRADE"); }
    public Material getUpgradeCannotMat()      { return getMaterial("upgrade.upgrade-cannot.material", Material.RED_WOOL); }
    public String  getUpgradeCannotName()      { return getString("upgrade.upgrade-cannot.name", "<red>✘ INSUFFICIENT TREASURY"); }
    public Material getUpgradeMaxMat()         { return getMaterial("upgrade.upgrade-max.material", Material.BARRIER); }
    public String  getUpgradeMaxName()         { return getString("upgrade.upgrade-max.name", "<red>Maximum Level Reached"); }
    public int     getUpgradeNextSlot()        { return getInt("upgrade.next-level.slot", 15); }
    public Material getUpgradeNextMat()        { return getMaterial("upgrade.next-level.material", Material.BEACON); }
    public String  getUpgradeNextName()        { return getString("upgrade.next-level.name", "<gold>Level {level} Preview"); }
    public int     getUpgradeCloseSlot()       { return getInt("upgrade.close.slot", 22); }

    // ── War ───────────────────────────────────────────────────

    public String  getWarTitle()   { return getString("war.title", "<dark_gray>[ <red>⚔ Clan War <dark_gray>]"); }
    public int     getWarSize()    { return getInt("war.size", 27); }
    public Material getWarFiller() { return getMaterial("war.filler", Material.GRAY_STAINED_GLASS_PANE); }
    public int     getWarStatusSlot()      { return getInt("war.status.slot", 11); }
    public Material getWarStatusNoWarMat() { return getMaterial("war.status.material-no-war", Material.CLOCK); }
    public Material getWarStatusActiveMat(){ return getMaterial("war.status.material-active", Material.IRON_SWORD); }
    public String  getWarStatusNoWarName() { return getString("war.status.name-no-war", "<gray>No Active War"); }
    public String  getWarStatusActiveName(){ return getString("war.status.name-active", "<red>⚔ War Status"); }
    public int     getWarActionSlot()      { return getInt("war.action.slot", 13); }
    public Material getWarActionOpenMat()  { return getMaterial("war.action.material-open", Material.GREEN_WOOL); }
    public Material getWarActionClosedMat(){ return getMaterial("war.action.material-closed", Material.BARRIER); }
    public String  getWarActionOpenName()  { return getString("war.action.name-open", "<green>⚔ Register / Leave War"); }
    public String  getWarActionClosedName(){ return getString("war.action.name-closed", "<gray>Registration Closed"); }
    public int     getWarClansSlot()       { return getInt("war.clans.slot", 15); }
    public String  getWarClansName()       { return getString("war.clans.name", "<white>Registered Clans ({count})"); }
    public String  getWarClansEmptyName()  { return getString("war.clans.name-empty", "<gray>No Clans Registered"); }
    public int     getWarCloseSlot()       { return getInt("war.close.slot", 22); }

    // ── Contribution Shop ─────────────────────────────────────

    public String  getContribShopTitle()   { return getString("contribution-shop.title", "<dark_gray>[ <aqua>Contribution Shop <dark_gray>]"); }
    public int     getContribShopSize()    { return getInt("contribution-shop.size", 54); }
    public Material getContribShopFiller() { return getMaterial("contribution-shop.filler", Material.CYAN_STAINED_GLASS_PANE); }
    public int     getContribShopPointsSlot()  { return getInt("contribution-shop.points-display.slot", 4); }
    public Material getContribShopPointsMat()  { return getMaterial("contribution-shop.points-display.material", Material.NETHER_STAR); }
    public String  getContribShopPointsName()  { return getString("contribution-shop.points-display.name", "<aqua>Your Contribution Points"); }
    public int     getContribShopCloseSlot()   { return getInt("contribution-shop.close.slot", 49); }
    public int     getContribShopPrevSlot()    { return getInt("contribution-shop.prev.slot", 45); }
    public int     getContribShopNextSlot()    { return getInt("contribution-shop.next.slot", 53); }

    // ── Coins Shop ────────────────────────────────────────────

    public String  getCoinsShopTitle()   { return getString("coins-shop.title", "<dark_gray>[ <yellow>Coins Shop <dark_gray>]"); }
    public int     getCoinsShopSize()    { return getInt("coins-shop.size", 54); }
    public Material getCoinsShopFiller() { return getMaterial("coins-shop.filler", Material.YELLOW_STAINED_GLASS_PANE); }
    public int     getCoinsShopBalanceSlot()  { return getInt("coins-shop.balance-display.slot", 4); }
    public Material getCoinsShopBalanceMat()  { return getMaterial("coins-shop.balance-display.material", Material.SUNFLOWER); }
    public String  getCoinsShopBalanceName()  { return getString("coins-shop.balance-display.name", "<yellow>Your {coins-name}: <gold>{balance}"); }
    public int     getCoinsShopCloseSlot()    { return getInt("coins-shop.close.slot", 49); }
    public int     getCoinsShopPrevSlot()     { return getInt("coins-shop.prev.slot", 45); }
    public int     getCoinsShopNextSlot()     { return getInt("coins-shop.next.slot", 53); }

    // ── Disband Confirm ───────────────────────────────────────

    public String  getDisbandConfirmTitle()   { return getString("disband-confirm.title", "<dark_gray>[ <red>⚠ Disband Clan <dark_gray>]"); }
    public int     getDisbandConfirmSize()    { return getInt("disband-confirm.size", 27); }
    public Material getDisbandConfirmFiller() { return getMaterial("disband-confirm.filler", Material.RED_STAINED_GLASS_PANE); }
    public int     getDisbandConfirmYesSlot() { return getInt("disband-confirm.confirm.slot", 11); }
    public Material getDisbandConfirmYesMat() { return getMaterial("disband-confirm.confirm.material", Material.LIME_WOOL); }
    public String  getDisbandConfirmYesName() { return getString("disband-confirm.confirm.name", "<green><bold>✔ CONFIRM DISBAND"); }
    public List<String> getDisbandConfirmYesLore() { return getStringList("disband-confirm.confirm.lore"); }
    public int     getDisbandConfirmNoSlot()  { return getInt("disband-confirm.cancel.slot", 15); }
    public Material getDisbandConfirmNoMat()  { return getMaterial("disband-confirm.cancel.material", Material.RED_WOOL); }
    public String  getDisbandConfirmNoName()  { return getString("disband-confirm.cancel.name", "<red><bold>✘ CANCEL"); }

    public FileConfiguration getConfig() { return cfg; }
}