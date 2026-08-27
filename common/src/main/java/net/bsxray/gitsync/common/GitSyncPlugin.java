package net.bsxray.gitsync.common;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Base plugin handling configuration and command dispatch.
 * Author: bsxray
 */
public abstract class GitSyncPlugin extends JavaPlugin {

    private Config config;

    /** Returns "upload" for the host plugin and "download" for the downloader plugin. */
    public abstract String operation();

    /**
     * Runs the actual sync operation asynchronously.
     * Implementations collect messages into {@code lines} and flush them on the main thread.
     */
    public abstract void run(Config cfg, List<String> lines);

    public Config config() {
        return config;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new Config(getConfig());
        getCommand("gitsync").setExecutor(this::onCommand);
        getLogger().info("GitSync-" + operation() + " aktiviert. Autor: bsxray");
    }

    @Override
    public void onDisable() {
        getLogger().info("GitSync-" + operation() + " deaktiviert.");
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = (args.length > 0) ? args[0].toLowerCase() : operation();

        switch (sub) {
            case "reload" -> {
                reloadConfig();
                config = new Config(getConfig());
                sender.sendMessage("[GitSync] Config neu geladen.");
            }
            case "status" -> {
                sender.sendMessage("§e[GitSync] " + operation().toUpperCase() + " Konfiguration:");
                sender.sendMessage("§e  Owner:  " + config.owner);
                sender.sendMessage("§e  Repo:   " + config.repo);
                sender.sendMessage("§e  Branch: " + config.branch);
                sender.sendMessage("§e  Pfad:   " + config.localPath);
                sender.sendMessage("§e  Repo-Pfad: " + (config.repoPathOrRoot().isEmpty() ? "/ (Wurzel)" : config.repoPathOrRoot()));
                sender.sendMessage("§e  Token:  " + (config.token.isEmpty() ? "NICHT gesetzt" : mask(config.token)));
            }
            default -> {
                if (!config.valid()) {
                    sender.sendMessage("§c[GitSync] Config unvollständig! Setze github.owner / github.repo / github.token in config.yml und nutze /gitsync reload.");
                    return true;
                }
                sender.sendMessage("§a[GitSync] Starte " + operation() + " ...");
                List<String> lines = new ArrayList<>();
                getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    run(config, lines);
                    getServer().getScheduler().runTask(this, () -> lines.forEach(sender::sendMessage));
                });
            }
        }
        return true;
    }

    private static String mask(String token) {
        if (token.length() <= 4) return "***";
        return token.substring(0, 4) + "******";
    }
}
