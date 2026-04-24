package me.bintanq.quantumclan.placeholder;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PlaceholderAPI expansion for QuantumClan.
 *
 * Per-player placeholders: %quantumclan_<identifier>%
 * Leaderboard: %quantumclan_top_<N>_<field>%
 *
 * N = 1 to 50.
 */
public class QuantumClanPlaceholder extends PlaceholderExpansion {

    private final QuantumClan plugin;

    public QuantumClanPlaceholder(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "quantumclan"; }
    @Override public @NotNull String getAuthor()     { return "bintanq"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()              { return true; }
    @Override public boolean canRegister()          { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // ── Leaderboard: top_N_field ──────────────────────────
        if (params.startsWith("top_")) {
            return resolveLeaderboard(params);
        }

        // ── Per-player ────────────────────────────────────────
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        ClanMember member = plugin.getClanManager().getMember(player.getUniqueId());

        return switch (params) {
            case "clan_name"            -> clan != null ? clan.getName() : "";
            case "clan_tag"             -> clan != null ? clan.getTag() : "";
            case "clan_tag_colored"     -> clan != null ? clan.getColoredTag() : "";
            case "clan_level"           -> clan != null ? String.valueOf(clan.getLevel()) : "";
            case "clan_reputation"      -> clan != null ? String.valueOf(clan.getReputation()) : "";
            case "clan_money"           -> clan != null ? String.valueOf(clan.getMoney()) : "";
            case "clan_members_online"  -> clan != null
                    ? String.valueOf(plugin.getClanManager().getOnlineCount(clan.getId())) : "";
            case "clan_members_total"   -> clan != null
                    ? String.valueOf(clan.getMemberCount()) : "";
            case "clan_shield_active"   -> clan != null
                    ? (clan.hasActiveShield() ? "true" : "false") : "false";
            case "clan_rank"            -> clan != null
                    ? resolveRank(clan) : "";
            case "player_role"          -> member != null ? member.getRole() : "";
            case "player_contribution"  -> member != null
                    ? String.valueOf(member.getContributionPoints()) : "";
            case "player_coins"         -> {
                long coins = 0;
                try {
                    coins = plugin.getCoinsProvider().getCoins(player.getUniqueId()).get();
                } catch (Exception ignored) {}
                yield String.valueOf(coins);
            }
            case "ally_count"           -> clan != null
                    ? String.valueOf(plugin.getSocialManager().getAllianceCount(clan.getId())) : "0";
            case "rival_count"          -> clan != null
                    ? String.valueOf(plugin.getSocialManager().getRivalryCount(clan.getId())) : "0";
            default -> {
                // ── Dynamic alliance/rivalry placeholders ─────
                if (clan != null && params.startsWith("ally_") && params.endsWith("_name")) {
                    yield resolveAllyByIndex(clan, params);
                }
                if (clan != null && params.startsWith("rival_") && params.endsWith("_name")) {
                    yield resolveRivalByIndex(clan, params);
                }
                yield null;
            }
        };
    }

    private String resolveRank(Clan clan) {
        int rank = plugin.getClanManager().getClanRank(clan.getId());
        return rank > 0 ? "#" + rank : "";
    }

    /**
     * Resolves leaderboard placeholders: top_N_field
     * e.g. top_1_name, top_3_reputation, top_10_tag_colored
     */
    private String resolveLeaderboard(String params) {
        // params = "top_N_field" → strip "top_"
        String rest = params.substring(4); // e.g. "1_name" or "10_tag_colored"

        int underscoreIdx = rest.indexOf('_');
        if (underscoreIdx == -1) return "";

        String rankStr = rest.substring(0, underscoreIdx);
        String field   = rest.substring(underscoreIdx + 1);

        int rank;
        try { rank = Integer.parseInt(rankStr); }
        catch (NumberFormatException e) { return ""; }

        if (rank < 1 || rank > 50) return "";

        List<Clan> leaderboard = plugin.getClanManager().getLeaderboard();
        if (rank > leaderboard.size()) return "";

        Clan clan = leaderboard.get(rank - 1);

        return switch (field) {
            case "name"        -> clan.getName();
            case "tag"         -> clan.getTag();
            case "tag_colored" -> clan.getColoredTag();
            case "reputation"  -> String.valueOf(clan.getReputation());
            case "level"       -> String.valueOf(clan.getLevel());
            case "members"     -> String.valueOf(clan.getMemberCount());
            case "leader"      -> {
                var leader = org.bukkit.Bukkit.getOfflinePlayer(clan.getLeaderUuid());
                yield leader.getName() != null ? leader.getName() : clan.getLeaderUuid().toString();
            }
            default -> "";
        };
    }

    /**
     * Resolves %quantumclan_ally_N_name% — e.g. ally_1_name, ally_2_name
     */
    private String resolveAllyByIndex(Clan clan, String params) {
        // params = "ally_N_name" → strip "ally_" and "_name"
        String middle = params.substring(5, params.length() - 5); // N
        int index;
        try { index = Integer.parseInt(middle); } catch (NumberFormatException e) { return ""; }
        if (index < 1) return "";
        var allies = plugin.getSocialManager().getAllies(clan.getId());
        if (index > allies.size()) return "";
        int i = 1;
        for (String allyId : allies) {
            if (i == index) {
                Clan allyClan = plugin.getClanManager().getClanById(allyId);
                return allyClan != null ? allyClan.getName() : "";
            }
            i++;
        }
        return "";
    }

    /**
     * Resolves %quantumclan_rival_N_name% — e.g. rival_1_name, rival_2_name
     */
    private String resolveRivalByIndex(Clan clan, String params) {
        // params = "rival_N_name" → strip "rival_" and "_name"
        String middle = params.substring(6, params.length() - 5); // N
        int index;
        try { index = Integer.parseInt(middle); } catch (NumberFormatException e) { return ""; }
        if (index < 1) return "";
        var rivals = plugin.getSocialManager().getRivals(clan.getId());
        if (index > rivals.size()) return "";
        int i = 1;
        for (String rivalId : rivals) {
            if (i == index) {
                Clan rivalClan = plugin.getClanManager().getClanById(rivalId);
                return rivalClan != null ? rivalClan.getName() : "";
            }
            i++;
        }
        return "";
    }
}