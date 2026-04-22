package me.bintanq.quantumclan.api;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class QuantumClanAPIImpl implements QuantumClanAPI {

    private final QuantumClan plugin;

    public QuantumClanAPIImpl(QuantumClan plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable ClanAPI getClan(@NotNull UUID playerUuid) {
        Clan clan = plugin.getClanManager().getClanByPlayer(playerUuid);
        return clan != null ? new ClanWrapper(clan) : null;
    }

    @Override
    public @Nullable ClanAPI getClanById(@NotNull String clanId) {
        Clan clan = plugin.getClanManager().getClanById(clanId);
        return clan != null ? new ClanWrapper(clan) : null;
    }

    @Override
    public @Nullable ClanAPI getClanByName(@NotNull String name) {
        Clan clan = plugin.getClanManager().getClanByName(name);
        return clan != null ? new ClanWrapper(clan) : null;
    }

    @Override
    public @Nullable ClanAPI getClanByTag(@NotNull String tag) {
        Clan clan = plugin.getClanManager().getClanByTag(tag);
        return clan != null ? new ClanWrapper(clan) : null;
    }

    @Override
    public @NotNull Collection<? extends ClanAPI> getAllClans() {
        return plugin.getClanManager().getAllClans().stream()
                .map(ClanWrapper::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public int getClanCount() {
        return plugin.getClanManager().getAllClans().size();
    }

    @Override
    public boolean isInClan(@NotNull UUID playerUuid) {
        return plugin.getClanManager().isInClan(playerUuid);
    }

    @Override
    public @Nullable ClanMemberAPI getMember(@NotNull UUID playerUuid) {
        ClanMember member = plugin.getClanManager().getMember(playerUuid);
        return member != null ? new ClanMemberWrapper(member) : null;
    }

    @Override
    public @NotNull List<? extends ClanAPI> getLeaderboard(int limit) {
        return plugin.getClanManager().getLeaderboard().stream()
                .limit(limit)
                .map(ClanWrapper::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public int getClanRank(@NotNull String clanId) {
        return plugin.getClanManager().getClanRank(clanId);
    }

    @Override
    public @NotNull CompletableFuture<Long> getCoins(@NotNull UUID playerUuid) {
        return plugin.getCoinsProvider().getCoins(playerUuid);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> grantCoins(@NotNull UUID playerUuid, long amount, @NotNull String reason) {
        return plugin.getCoinsProvider().deposit(playerUuid, (double) amount, reason);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> deductCoins(@NotNull UUID playerUuid, long amount, @NotNull String reason) {
        return plugin.getCoinsProvider().withdraw(playerUuid, (double) amount);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> addReputation(@NotNull String clanId, int amount) {
        return plugin.getClanManager().addReputation(clanId, amount);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> addContributionPoints(@NotNull UUID playerUuid, int points) {
        return plugin.getClanManager().addContributionPoints(playerUuid, points);
    }

    @Override
    public boolean isWarActive() {
        return plugin.getWarManager().getActiveSession() != null && plugin.getWarManager().getActiveSession().isActive();
    }

    @Override
    public @Nullable WarSessionAPI getActiveWar() {
        WarSession session = plugin.getWarManager().getActiveSession();
        return session != null ? new WarSessionWrapper(session) : null;
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    // Wrappers --------------------------------------------------------------------------------------------------------

    private static class ClanWrapper implements ClanAPI {
        private final Clan clan;

        public ClanWrapper(Clan clan) {
            this.clan = clan;
        }

        @Override public @NotNull String getId() { return clan.getId(); }
        @Override public @NotNull String getName() { return clan.getName(); }
        @Override public @NotNull String getTag() { return clan.getTag(); }
        @Override public @NotNull String getTagColor() { return clan.getTagColor() == null ? "" : clan.getTagColor(); }
        @Override public @NotNull String getColoredTag() { return clan.getColoredTag(); }
        @Override public @NotNull String getFormattedTag() { return clan.getFormattedTag(); }
        @Override public @NotNull UUID getLeaderUuid() { return clan.getLeaderUuid(); }
        @Override public int getLevel() { return clan.getLevel(); }
        @Override public long getMoney() { return clan.getMoney(); }
        @Override public int getReputation() { return clan.getReputation(); }
        @Override public @NotNull List<UUID> getMemberUuids() { return clan.getMemberUuids(); }
        @Override public int getMemberCount() { return clan.getMemberCount(); }
        @Override public boolean hasMember(@NotNull UUID uuid) { return clan.hasMember(uuid); }
        @Override public @NotNull List<ClanHomeAPI> getHomes() {
            return clan.getHomes();
        }
        @Override public int getHomeCount() { return clan.getHomeCount(); }
        @Override public @Nullable ClanHomeAPI getHome(@NotNull String name) {
            return clan.getHome(name);
        }
        @Override public boolean hasActiveShield() { return clan.hasActiveShield(); }
        @Override public @Nullable Instant getShieldUntil() { return clan.getShieldUntil(); }
        @Override public @Nullable String getHallWorld() { return clan.getHallWorld(); }
        @Override public double getHallX() { return clan.getHallX(); }
        @Override public double getHallY() { return clan.getHallY(); }
        @Override public double getHallZ() { return clan.getHallZ(); }
        @Override public @NotNull Instant getCreatedAt() { return clan.getCreatedAt(); }
    }

    private static class ClanMemberWrapper implements ClanMemberAPI {
        private final ClanMember member;
        public ClanMemberWrapper(ClanMember member) { this.member = member; }
        @Override public @NotNull UUID getUuid() { return member.getUuid(); }
        @Override public @NotNull String getClanId() { return member.getClanId(); }
        @Override public @NotNull String getRole() { return member.getRole(); }
        @Override public int getContributionPoints() { return member.getContributionPoints(); }
        @Override public @NotNull Instant getJoinedAt() { return member.getJoinedAt(); }
    }

    private static class WarSessionWrapper implements WarSessionAPI {
        private final WarSession session;
        public WarSessionWrapper(WarSession session) { this.session = session; }
        @Override public @NotNull String getId() { return session.getId(); }
        @Override
        public @NotNull Format getFormat() {
            return Format.valueOf(session.getFormat().name());
        }
        @Override
        public @NotNull State getState() {
            return State.valueOf(session.getState().name());
        }
        @Override public boolean isActive() { return session.isActive(); }
        @Override public @NotNull Instant getStartedAt() { return session.getStartedAt(); }
        @Override public @Nullable Instant getEndedAt() { return session.getEndedAt(); }
        @Override public @Nullable String getWinnerClanId() { return session.getWinnerClanId(); }
        @Override public @NotNull Set<String> getRegisteredClanIds() { return session.getRegisteredClanIds(); }
        @Override public boolean isClanRegistered(@NotNull String clanId) { return session.isClanRegistered(clanId); }
        @Override public boolean isMemberParticipating(@NotNull UUID uuid) { return session.isMemberParticipating(uuid); }
        @Override public boolean isMemberAlive(@NotNull UUID uuid) { return session.isMemberAlive(uuid); }
        @Override public int getKillCount(@NotNull UUID uuid) { return session.getKillCount(uuid); }
        @Override public int getClanKillCount(@NotNull String clanId) { return session.getClanKillCount(clanId); }
        @Override public @NotNull Map<String, Integer> getAllClanKillCounts() { return session.getAllClanKillCounts(); }
    }
}
