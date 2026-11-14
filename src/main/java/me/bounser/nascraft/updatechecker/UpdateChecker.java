package me.bounser.nascraft.updatechecker;

import me.bounser.nascraft.Nascraft;
import me.bounser.nascraft.config.Config;
import me.bounser.nascraft.scheduler.FoliaScheduler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 启动时查询 GitHub 仓库的最新 Release，并与当前插件版本比较。
 *
 * <p>结果保存在静态字段中，供 {@link UpdateNotifyListener} 在 OP 进服时提醒。
 * 查询是异步的，不会阻塞服务器启动。</p>
 */
public final class UpdateChecker {

    private static final String RELEASES_API = "https://api.github.com/repos/llsgllsg/Nascraft26P/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/llsgllsg/Nascraft26P/releases";

    private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL_PATTERN = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");

    private static volatile String latestVersion;
    private static volatile String latestUrl;
    private static volatile String currentVersion;

    private UpdateChecker() {
    }

    /**
     * 异步检查更新。配置中关闭 update-check 时直接返回。
     */
    public static void check(Nascraft plugin) {
        if (!Config.getInstance().getUpdateCheckEnabled()) {
            return;
        }

        FoliaScheduler.runAsync(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API))
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Nascraft26P-UpdateChecker")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return;
                }

                String body = response.body();
                String tag = firstMatch(TAG_PATTERN, body);
                if (tag == null) {
                    return;
                }

                String current = getPluginVersion(plugin);
                currentVersion = current;

                if (UpdateVersion.compare(tag, current) > 0) {
                    latestVersion = tag;
                    String url = firstMatch(URL_PATTERN, body);
                    latestUrl = url != null ? url : RELEASES_PAGE;
                    plugin.getLogger().info("发现新版本 " + tag + "（当前 " + current + "），下载地址：" + latestUrl);
                }
            } catch (Exception exception) {
                plugin.getLogger().log(Level.FINE, "更新检查失败：" + exception.getMessage());
            }
        });
    }

    private static String getPluginVersion(Nascraft plugin) {
        try {
            return plugin.getPluginMeta().getVersion();
        } catch (Throwable ignored) {
            return plugin.getDescription().getVersion();
        }
    }

    private static String firstMatch(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static boolean isUpdateAvailable() {
        return latestVersion != null;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static String getLatestUrl() {
        return latestUrl;
    }

    public static String getCurrentVersion() {
        return currentVersion;
    }
}
