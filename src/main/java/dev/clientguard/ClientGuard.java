package dev.clientguard;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ClientGuard extends JavaPlugin implements Listener {

    private List<String> blockedBrands;
    private List<String> blockedChannels;
    private String banCommand;
    private String banReason;
    private boolean ignoreBedrock;
    private boolean kickOnly;
    private boolean notifyAdmins;
    private long delay;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ClientGuard aktif.");
    }

    private void loadSettings() {
        reloadConfig();
        blockedBrands = lower(getConfig().getStringList("blocked-brands"));
        blockedChannels = lower(getConfig().getStringList("blocked-channels"));
        banCommand = getConfig().getString("ban-command", "ban %player% %reason%");
        banReason = getConfig().getString("ban-reason", "Yasaklı client");
        ignoreBedrock = getConfig().getBoolean("ignore-bedrock", true);
        kickOnly = getConfig().getBoolean("kick-only", false);
        notifyAdmins = getConfig().getBoolean("notify-admins", true);
        delay = getConfig().getLong("check-delay-ticks", 60);
    }

    private static List<String> lower(List<String> in) {
        return in.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("clientguard.bypass")) return;
        if (ignoreBedrock && isBedrock(player)) return;

        Bukkit.getScheduler().runTaskLater(this, () -> check(player), delay);
    }

    private void check(Player player) {
        if (!player.isOnline()) return;
        // Brand geldikten sonra Bedrock tekrar kontrol edilir (Geyser brand'i)
        if (ignoreBedrock && isBedrock(player)) return;

        String brand = player.getClientBrandName();
        if (brand != null) {
            String b = brand.toLowerCase(Locale.ROOT);
            for (String bad : blockedBrands) {
                if (b.contains(bad)) {
                    punish(player, "brand=" + brand);
                    return;
                }
            }
        }

        Set<String> channels = player.getListeningPluginChannels();
        for (String ch : channels) {
            String c = ch.toLowerCase(Locale.ROOT);
            for (String bad : blockedChannels) {
                if (c.contains(bad)) {
                    punish(player, "channel=" + ch);
                    return;
                }
            }
        }
    }

    /** Geyser/Floodgate oyuncusu mu? */
    private boolean isBedrock(Player player) {
        UUID id = player.getUniqueId();
        // Floodgate UUID'lerinin üst 64 biti 0'dır
        if (id.getMostSignificantBits() == 0L) return true;
        String brand = player.getClientBrandName();
        return brand != null && brand.toLowerCase(Locale.ROOT).contains("geyser");
    }

    private void punish(Player player, String detail) {
        String name = player.getName();
        getLogger().warning(name + " yasaklı client tespit edildi (" + detail + ")");

        if (notifyAdmins) {
            String msg = "§c[ClientGuard] §f" + name + " §7-> §e" + detail;
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("clientguard.admin"))
                    .forEach(p -> p.sendMessage(msg));
        }

        if (kickOnly) {
            player.kickPlayer(banReason);
            return;
        }
        String cmd = banCommand.replace("%player%", name).replace("%reason%", banReason);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        if (player.isOnline()) player.kickPlayer(banReason);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage("§aClientGuard ayarları yenilendi.");
            return true;
        }
        sender.sendMessage("§eKullanım: /clientguard reload");
        return true;
    }
}
