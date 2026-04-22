package me.bintanq.quantumclan.command.admin;

import me.bintanq.quantumclan.QuantumClan;
import me.bintanq.quantumclan.model.WarSession;
import org.bukkit.entity.Player;

public class AdminWarCommand {
    private final QuantumClan plugin;
    public AdminWarCommand(QuantumClan plugin) { this.plugin = plugin; }
    public void execute(Player player, String[] args) {
        if (args.length == 0) { plugin.sendRaw(player, "<red>/qclanadmin war <start|end>"); return; }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (plugin.getWarManager().getActiveSession() != null) { plugin.sendRaw(player, "<red>War sudah berjalan."); return; }
                plugin.getWarManager().createSession();
                plugin.getWarManager().startWar();
                plugin.sendMessage(player, "admin.war-started");
            }
            case "end" -> {
                WarSession war = plugin.getWarManager().getActiveSession();
                if (war == null || !war.isActive()) { plugin.sendMessage(player, "admin.war-no-active"); return; }
                plugin.getWarManager().endWar(null);
                plugin.sendMessage(player, "admin.war-ended");
            }
            default -> plugin.sendRaw(player, "<red>/qclanadmin war <start|end>");
        }
    }
}