package net.bsxray.gitsync.common;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Holds the settings loaded from config.yml.
 * Author: bsxray
 */
public final class Config {
    public final String owner;
    public final String repo;
    public final String token;
    public final String branch;
    public final String localPath;
    public final String repoPath;
    public final String commitMessage;
    public final boolean deleteExtraFiles;

    public Config(FileConfiguration c) {
        this.owner = c.getString("github.owner", "");
        this.repo = c.getString("github.repo", "");
        this.token = c.getString("github.token", "");
        this.branch = c.getString("github.branch", "main");
        this.localPath = c.getString("sync.path", "plugins");
        this.repoPath = c.getString("sync.repo-path", "");
        this.commitMessage = c.getString("sync.commit-message", "GitSync update");
        this.deleteExtraFiles = c.getBoolean("sync.delete-extra-files", false);
    }

    public boolean valid() {
        return !owner.isEmpty() && !repo.isEmpty() && !token.isEmpty();
    }

    public String repoPathOrRoot() {
        return repoPath.isEmpty() ? "" : repoPath;
    }
}
