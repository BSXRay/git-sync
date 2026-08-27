package net.bsxray.gitsync.host;

import net.bsxray.gitsync.common.Config;
import net.bsxray.gitsync.common.GitSyncPlugin;
import net.bsxray.gitsync.common.SyncEngine;
import net.bsxray.gitsync.common.UploadResult;

import java.util.List;

/**
 * gitsync-host - lädt konfigurierte Ordner von diesem Server in eine private GitHub-Repo hoch.
 * Author: bsxray
 */
public final class HostPlugin extends GitSyncPlugin {

    @Override
    public String operation() {
        return "upload";
    }

    @Override
    public void run(Config cfg, List<String> lines) {
        lines.add("§e[GitSync] Lade hoch von: " + cfg.localPath + " -> Repo " + cfg.owner + "/" + cfg.repo);
        getLogger().info("Starte Upload von " + cfg.localPath + " nach " + cfg.owner + "/" + cfg.repo);
        try {
            UploadResult r = SyncEngine.upload(cfg, m -> {
                lines.add("§7" + m);
                getLogger().info(m);
            });
            lines.add("§a[GitSync] Fertig. Hochgeladen: " + r.uploaded + ", Fehler: " + r.errors
                    + ", Gesamt: " + r.total + " Dateien.");
            getLogger().info("Upload fertig. Hochgeladen: " + r.uploaded + ", Fehler: " + r.errors
                    + ", Gesamt: " + r.total + " Dateien.");
        } catch (Exception e) {
            lines.add("§c[GitSync] Fehler beim Upload: " + e.getMessage());
            getLogger().severe("Upload-Fehler: " + e.getMessage());
            getLogger().log(java.util.logging.Level.WARNING, "Upload-Ausnahme (Stacktrace):", e);
        }
    }
}
