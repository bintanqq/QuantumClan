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
            default -> null;
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
}