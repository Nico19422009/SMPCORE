package by.nicojerema.smpcore;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.logging.Level;

public class UpdateService {
    private static final String GITHUB_API_RELEASE_URL = "https://api.github.com/repos/%s/%s/releases/latest";

    private final SMPCorePlugin plugin;
    private final HttpClient httpClient;
    private boolean enabled;
    private String downloadName;

    public UpdateService(SMPCorePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        reloadConfig();
    }

    public void reloadConfig() {
        this.enabled = plugin.getConfig().getBoolean("update.enabled", false);
        this.downloadName = plugin.getConfig().getString("update.download-name", "SMPCORE-update.jar").trim();
        if (this.downloadName.isEmpty()) {
            this.downloadName = "SMPCORE-update.jar";
        }
    }

    public void downloadLatestRelease(CommandSender sender) {
        if (!enabled) {
            sendMessage(sender, "&c/smpcore update is currently disabled in config.");
            return;
        }

        String owner = UpdateCredentials.GITHUB_OWNER.trim();
        String repo = UpdateCredentials.GITHUB_REPO.trim();
        String assetName = UpdateCredentials.GITHUB_ASSET_NAME.trim();
        String token = UpdateCredentials.GITHUB_TOKEN.trim();
        if (owner.isEmpty() || repo.isEmpty() || assetName.isEmpty() || token.isEmpty()) {
            sendMessage(sender, "&cUpdate credentials are missing. Edit UpdateCredentials.java.");
            return;
        }

        sendMessage(sender, "&eDownloading latest SMPCORE release from GitHub. This may take a moment...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = String.format(GITHUB_API_RELEASE_URL, owner, repo);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Accept", "application/vnd.github+json")
                        .header("Authorization", "token " + token)
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    String message = "&cGitHub API request failed with status " + response.statusCode();
                    sendMessage(sender, message);
                    plugin.getLogger().warning(message);
                    return;
                }

                String downloadUrl = findAssetDownloadUrl(response.body(), assetName);
                if (downloadUrl == null) {
                sendMessage(sender, "&cCould not locate asset &f" + assetName + "&c in the latest release.");
                    plugin.getLogger().warning("Update: asset " + assetName + " not found in release.");
                    return;
                }

                Path targetDir = plugin.getDataFolder().getParentFile().toPath();
                Path tempFile = Files.createTempFile(targetDir, "smpcore-update-", ".tmp");
                HttpRequest downloadRequest = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .header("Authorization", "token " + token)
                        .timeout(Duration.ofMinutes(2))
                        .GET()
                        .build();

                HttpResponse<Path> downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofFile(tempFile));
                if (downloadResponse.statusCode() != 200) {
                    sendMessage(sender, "&cDownload failed with status " + downloadResponse.statusCode());
                    plugin.getLogger().warning("Failed to download asset: status " + downloadResponse.statusCode());
                    Files.deleteIfExists(tempFile);
                    return;
                }

                Path finalPath = resolveTargetPath(downloadName);
                Path finalParent = finalPath.getParent();
                if (finalParent != null) {
                    Files.createDirectories(finalParent);
                }
                try {
                    Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException ex) {
                    Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
                }

                sendMessage(sender, "&aDownloaded latest SMPCORE to &f" + finalPath.getFileName());
                sendMessage(sender, "&eRestart the server to load the new jar: &f" + finalPath);
                if (!UpdateCredentials.GITHUB_RELEASE_LINK.isBlank()) {
                    sendMessage(sender, "&7Release page: &f" + UpdateCredentials.GITHUB_RELEASE_LINK);
                }
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                String message = "&cGitHub update failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                plugin.getLogger().log(Level.WARNING, "GitHub update failed", ex);
                sendMessage(sender, message);
            }
        });
    }

    private Path resolveTargetPath(String configuredName) {
        Path candidate = Path.of(configuredName);
        if (candidate.isAbsolute()) {
            return candidate;
        }

        if (candidate.getNameCount() > 0 && candidate.getName(0).toString().equalsIgnoreCase("plugins")) {
            if (candidate.getNameCount() == 1) {
                candidate = Path.of("SMPCORE-update.jar");
            } else {
                candidate = candidate.subpath(1, candidate.getNameCount());
            }
        }

        Path pluginsDir = plugin.getDataFolder().getParentFile().toPath();
        return pluginsDir.resolve(candidate);
    }

    private String findAssetDownloadUrl(String releaseJson, String assetName) {
        String needle = "\"name\":\"" + assetName + "\"";
        int assetIndex = releaseJson.indexOf(needle);
        if (assetIndex < 0) {
            return null;
        }
        String urlKey = "\"browser_download_url\":\"";
        int urlIndex = releaseJson.indexOf(urlKey, assetIndex);
        if (urlIndex < 0) {
            return null;
        }
        int start = urlIndex + urlKey.length();
        int end = releaseJson.indexOf('\"', start);
        if (end < 0) {
            return null;
        }
        String raw = releaseJson.substring(start, end);
        return raw.replace("\\/", "/");
    }

    private void sendMessage(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(TextUtil.colorize(message)));
    }
}
