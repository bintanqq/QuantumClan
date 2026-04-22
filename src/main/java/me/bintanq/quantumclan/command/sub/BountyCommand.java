package me.bintanq.quantumclan.command.sub;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.gui.BountyBoardGUI;
import me.bintanq.quantumclan.listener.PlayerDeathListener;
import me.bintanq.quantumclan.model.Clan;
import me.bintanq.quantumclan.model.ClanMember;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class BountyCommand {
    private final QuantumClan plugin;
    public BountyCommand(QuantumClan plugin) { this.plugin = plugin; }

    public void execute(Player player, String[] args) {
        if (args.length == 0) { plugin.sendRaw(player, "<red>/qclan bounty <place|board|submit>"); return; }
        switch (args[0].toLowerCase()) {
            case "place"  -> handlePlace(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "board"  -> handleBoard(player);
            case "submit" -> handleSubmit(player);
            default       -> plugin.sendRaw(player, "<red>/qclan bounty <place|board|submit>");
        }
    }

    private void handlePlace(Player player, String[] args) {
        if (!player.hasPermission("quantumclan.bounty.place")) { plugin.sendMessage(player, "error.no-permission"); return; }
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        if (!plugin.getClanManager().hasRolePermission(player.getUniqueId(), "can-declare-bounty")) {
            plugin.sendMessage(player, "error.role-no-permission", "{role}", plugin.getClanManager().getMember(player.getUniqueId()).getRole()); return;
        }
        if (args.length == 0) { plugin.sendRaw(player, "<red>/qclan bounty place <player>"); return; }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { plugin.sendMessage(player, "error.player-not-found", "{player}", args[0]); return; }
        if (target.equals(player)) { plugin.sendMessage(player, "bounty.place-self"); return; }
        ClanMember targetMember = plugin.getClanManager().getMember(target.getUniqueId());
        if (targetMember != null && targetMember.getClanId().equals(clan.getId())) { plugin.sendMessage(player, "bounty.place-same-clan"); return; }
        if (plugin.getBountyManager().hasBounty(target.getUniqueId())) { plugin.sendMessage(player, "bounty.place-already-active"); return; }
        Clan targetClan = targetMember != null ? plugin.getClanManager().getClanById(targetMember.getClanId()) : null;
        if (targetClan != null && targetClan.hasActiveShield()) { plugin.sendMessage(player, "bounty.place-shielded"); return; }
        double minAmount = plugin.getConfigManager().getBountyMinAmount();
        plugin.getChatInputManager().prompt(player,
                plugin.getMessagesManager().get("bounty.place-prompt", "{min}", String.valueOf((long) minAmount)),
                input -> {
                    long amount;
                    try { amount = Long.parseLong(input); } catch (NumberFormatException e) { plugin.sendMessage(player, "error.invalid-number"); return; }
                    if (amount < (long) minAmount) { plugin.sendMessage(player, "error.invalid-amount", "{value}", String.valueOf((long) minAmount)); return; }
                    plugin.getEconomyProvider().withdraw(player, amount).thenAccept(ok ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!ok) { plugin.sendMessage(player, "bounty.place-insufficient"); return; }
                                plugin.getBountyManager().placeBounty(player.getUniqueId(), target.getUniqueId(), amount)
                                        .thenAccept(placed -> Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (!placed) { plugin.getEconomyProvider().deposit(player, amount); plugin.sendRaw(player, "<red>Gagal memasang bounty."); return; }
                                            plugin.sendMessage(player, "bounty.place-success", "{value}", plugin.getEconomyProvider().format(amount));
                                        }));
                            }));
                },
                () -> plugin.sendMessage(player, "bounty.place-cancelled"));
    }

    private void handleBoard(Player player) {
        if (!player.hasPermission("quantumclan.bounty.board")) { plugin.sendMessage(player, "error.no-permission"); return; }
        BountyBoardGUI.open(plugin, player);
    }

    private void handleSubmit(Player player) {
        if (!player.hasPermission("quantumclan.bounty.submit")) { plugin.sendMessage(player, "error.no-permission"); return; }
        if (plugin.getClanManager().getClanByPlayer(player.getUniqueId()) == null) { plugin.sendMessage(player, "clan.not-in-clan"); return; }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.PLAYER_HEAD || !hand.hasItemMeta()) {
            plugin.sendMessage(player, "bounty.submit-no-item"); return;
        }
        PersistentDataContainer pdc = hand.getItemMeta().getPersistentDataContainer();
        NamespacedKey keyHeadId   = new NamespacedKey(plugin, PlayerDeathListener.PDC_BOUNTY_HEAD_ID);
        NamespacedKey keyBountyId = new NamespacedKey(plugin, PlayerDeathListener.PDC_BOUNTY_ID);
        String headId   = pdc.get(keyHeadId,   PersistentDataType.STRING);
        String bountyId = pdc.get(keyBountyId, PersistentDataType.STRING);
        if (headId == null || bountyId == null) { plugin.sendMessage(player, "bounty.submit-invalid"); return; }
        // Remove head from inventory BEFORE processing (anti-dupe)
        hand.setAmount(hand.getAmount() - 1);
        plugin.getBountyManager().submitHead(player, headId, bountyId).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!ok) {
                        // Re-give head on failure? No — once removed, done. Just notify.
                        plugin.sendMessage(player, "bounty.submit-already-claimed");
                    }
                }));
    }
}
