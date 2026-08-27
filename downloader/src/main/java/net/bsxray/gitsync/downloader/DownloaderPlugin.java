package net.bsxray.gitsync.downloader;

import net.bsxray.gitsync.common.Config;
import net.bsxray.gitsync.common.DownloadResult;
import net.bsxray.gitsync.common.GitSyncPlugin;
import net.bsxray.gitsync.common.SyncEngine;

import java.util.List;

/**
 * gitsync-downloader - lädt den Inhalt einer privaten GitHub-Repo in einen konfigurierten
 * Ordner auf diesem Server und überschreibt dabei die bestehenden Dateien.
 * Author: bsxray
 */
public final class DownloaderPlugin extends GitSyncPlugin {

    @Override
    public String operation() {
        return "download";
    }

    @Override
    public void run(Config cfg, List<String> lines) {
        lines.add("§e[GitSync] Lade herunter aus Repo " + cfg.owner + "/" + cfg.repo + " -> " + cfg.localPath);
        try {
            DownloadResult r = SyncEngine.download(cfg, m -> lines.add("§7" + m));
            lines.add("§a[GitSync] Fertig. Heruntergeladen: " + r.downloaded + ", Fehler: " + r.errors
                    + ", Entfernt: " + r.removed + " Dateien.");
        } catch (Exception e) {
            lines.add("§c[GitSync] Fehler beim Download: " + e.getMessage());
            getLogger().severe("Download-Fehler: " + e.getMessage());
        }
    }
}
