package by.nicojerema.smpcore;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SidebarService {
    private static final String OBJECTIVE_ID = "smpcore_sb";
    private static final String DEFAULT_RANK_PREFIX_FORMAT = "%color%[%name%] ";
    private static final String TEAM_ID_PREFIX = "sb_l";
    private static final int MIN_SIDEBAR_SIZE = 6;
    private static final int MAX_SIDEBAR_SIZE = 15;
    private static final char[] ENTRY_COLORS = "0123456789abcdef".toCharArray();

    private final SMPCorePlugin plugin;
    private final PlayerDataStore dataStore;

    public SidebarService(SMPCorePlugin plugin, PlayerDataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public void refreshAll() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayers) {
            refresh(player);
        }
    }

    public void reloadFromConfig() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayers) {
            resetSidebar(player);
            refresh(player);
        }
    }

    public void refresh(Player viewer) {
        Scoreboard board = viewer.getScoreboard();
        if (board == null
                || board == Bukkit.getScoreboardManager().getMainScoreboard()
                || board.getObjective(OBJECTIVE_ID) == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            viewer.setScoreboard(board);
            initializeSidebar(board, viewer);
        }

        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            initializeSidebar(board, viewer);
            objective = board.getObjective(OBJECTIVE_ID);
            if (objective == null) {
                return;
            }
        }

        updateSidebarLines(viewer, board, objective);
        updateRankTeams(board);
        applyTab(viewer);
    }

    public String replacePlaceholders(Player player, String input) {
        RankInfo rankInfo = getRankInfo(dataStore.getRank(player.getUniqueId()));
        String serverName = plugin.getConfig().getString("server-name", "DUMMYSMP");
        String coins;
        if (plugin.isEconomyEnabled()) {
            coins = TextUtil.formatCoins(dataStore.getCoins(player.getUniqueId()));
        } else {
            coins = plugin.getConfig().getString("economy.disabled-placeholder", "Disabled");
        }
        String playtime = TextUtil.formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE));

        return input
                .replace("%player%", player.getName())
                .replace("%rank%", rankInfo.getDisplayName())
                .replace("%rank_prefix%", rankInfo.getPrefix().trim())
                .replace("%coins%", coins)
                .replace("%playtime%", playtime)
                .replace("%server_name%", serverName);
    }

    private void resetSidebar(Player player) {
        Scoreboard board = player.getScoreboard();
        if (board == null || board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            return;
        }

        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective != null) {
            objective.unregister();
        }
    }

    private void initializeSidebar(Scoreboard board, Player viewer) {
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = board.registerNewObjective(
                    OBJECTIVE_ID,
                    "dummy",
                    TextUtil.colorize(getSidebarTitle(viewer))
            );
        }

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());

        int size = getSidebarSize();
        for (int i = 0; i < size; i++) {
            setupLine(board, objective, teamId(i), entryForLine(i), size - i);
        }
    }

    private void setupLine(Scoreboard board, Objective objective, String teamId, String entry, int score) {
        Team team = board.getTeam(teamId);
        if (team == null) {
            team = board.registerNewTeam(teamId);
            team.addEntry(entry);
        }
        objective.getScore(entry).setScore(score);
    }

    private void setLineText(Scoreboard board, String teamId, String text) {
        Team team = board.getTeam(teamId);
        if (team == null) {
            return;
        }
        String colored = TextUtil.colorize(text);
        team.setPrefix(TextUtil.safePrefix(colored, 64));
    }

    private void updateSidebarLines(Player viewer, Scoreboard board, Objective objective) {
        objective.setDisplayName(TextUtil.colorize(getSidebarTitle(viewer)));
        objective.numberFormat(NumberFormat.blank());
        int size = getSidebarSize();
        List<String> lines = buildSidebarTextLines(viewer, size);
        for (int i = 0; i < size; i++) {
            setLineText(board, teamId(i), lines.get(i));
        }
    }

    private void updateRankTeams(Scoreboard board) {
        Map<String, RankInfo> rankMap = loadRanks();
        RankInfo fallbackRank = rankMap.values().stream()
                .findFirst()
                .orElse(new RankInfo("&7[Member] ", "&7Member", 100));

        clearRankEntries(board);

        for (Player target : Bukkit.getOnlinePlayers()) {
            String playerRank = dataStore.getRank(target.getUniqueId());
            RankInfo rankInfo = rankMap.getOrDefault(playerRank, fallbackRank);
            String teamId = buildRankTeamId(playerRank, rankInfo.getWeight());

            Team team = board.getTeam(teamId);
            if (team == null) {
                team = board.registerNewTeam(teamId);
            }
            team.setPrefix(TextUtil.safePrefix(TextUtil.colorize(rankInfo.getPrefix()), 64));
            team.addEntry(target.getName());
        }
    }

    private void clearRankEntries(Scoreboard board) {
        for (Team team : board.getTeams()) {
            if (!team.getName().startsWith("rk")) {
                continue;
            }
            Set<String> entries = new HashSet<>(team.getEntries());
            for (String entry : entries) {
                team.removeEntry(entry);
            }
        }
    }

    private String buildRankTeamId(String rankKey, int weight) {
        int normalizedWeight = Math.max(0, Math.min(999, weight));
        int hash = rankKey == null ? 0 : (rankKey.toLowerCase(Locale.ROOT).hashCode() & 0xFFFF);
        return String.format(Locale.ROOT, "rk%03d%04x", normalizedWeight, hash);
    }

    private void applyTab(Player player) {
        List<String> headerLines = plugin.getConfig().getStringList("tab.header");
        List<String> footerLines = plugin.getConfig().getStringList("tab.footer");

        String header = buildMultiline(player, headerLines);
        String footer = buildMultiline(player, footerLines);
        String discordRaw = plugin.getConfig().getString("tab.discord-link", "").trim();
        if (!discordRaw.isEmpty()) {
            String discordLine = replacePlaceholders(player, discordRaw);
            if (!discordLine.isEmpty()) {
                header = discordLine + "\n" + header;
            }
        }

        player.setPlayerListHeaderFooter(TextUtil.colorize(header), TextUtil.colorize(footer));
    }

    private String buildMultiline(Player player, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return replacePlaceholders(player, "%server_name%");
        }

        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(replacePlaceholders(player, line));
        }
        return String.join("\n", out);
    }

    private int getSidebarSize() {
        int configured = plugin.getConfig().getInt("sidebar.size", MIN_SIDEBAR_SIZE);
        if (configured < MIN_SIDEBAR_SIZE) {
            return MIN_SIDEBAR_SIZE;
        }
        if (configured > MAX_SIDEBAR_SIZE) {
            return MAX_SIDEBAR_SIZE;
        }
        return configured;
    }

    private List<String> buildSidebarTextLines(Player viewer, int size) {
        String coinsName = plugin.getConfig().getString("coins-display-name", "Coins");
        String playtime = TextUtil.formatPlaytime(viewer.getStatistic(Statistic.PLAY_ONE_MINUTE));
        long coinsValue = plugin.isEconomyEnabled()
                ? dataStore.getCoins(viewer.getUniqueId())
                : 0L;

        List<String> lines = new ArrayList<>();
        lines.add("&7");
        lines.add("&e" + coinsName + ":");
        if (plugin.isEconomyEnabled()) {
            lines.add("&f" + TextUtil.formatCoins(coinsValue));
        } else if (plugin.getConfig().getBoolean("economy.show-coins-in-sidebar-when-disabled", false)) {
            String disabledValue = plugin.getConfig().getString("economy.disabled-placeholder", "Disabled");
            lines.add("&c" + disabledValue);
        } else {
            String disabledLine = plugin.getConfig().getString("economy.disabled-sidebar-line", "&cEconomy disabled");
            lines.add(disabledLine);
        }

        boolean showSecondary = plugin.isSecondaryCurrencyEnabled()
                && plugin.getPlayerDataStore().isSidebarSecondaryVisible(viewer.getUniqueId());
        if (showSecondary) {
            lines.add("&7");
            lines.add("&e" + plugin.getSecondaryCurrencyDisplayName() + ":");
            lines.add("&f" + TextUtil.formatCoins(dataStore.getSecondaryCurrency(viewer.getUniqueId())));
        }

        lines.add("&7");
        lines.add("&ePlaytime:");
        lines.add("&f" + playtime);

        List<String> finalLines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            finalLines.add(i < lines.size() ? lines.get(i) : "&7");
        }
        return finalLines;
    }

    private String teamId(int index) {
        return TEAM_ID_PREFIX + (index + 1);
    }

    private String entryForLine(int index) {
        char first = ENTRY_COLORS[(index / ENTRY_COLORS.length) % ENTRY_COLORS.length];
        char second = ENTRY_COLORS[index % ENTRY_COLORS.length];
        return "\u00A7" + first + "\u00A7" + second;
    }

    private Map<String, RankInfo> loadRanks() {
        Map<String, RankInfo> ranks = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("ranks");
        if (section == null) {
            ranks.put("member", new RankInfo("&7[Member] ", "&7Member", 100));
            return ranks;
        }

        String prefixFormat = plugin.getConfig().getString("rank-prefix-format", DEFAULT_RANK_PREFIX_FORMAT);
        for (String key : section.getKeys(false)) {
            String normalized = key.toLowerCase(Locale.ROOT);
            String displayName = section.getString(key + ".name", section.getString(key + ".display-name", toDisplayName(key)));
            String color = section.getString(key + ".color", "&7");
            String prefix = section.getString(key + ".prefix", "");

            if (prefix == null || prefix.trim().isEmpty()) {
                prefix = prefixFormat;
            }

            prefix = prefix
                    .replace("%name%", displayName)
                    .replace("%color%", color);

            String rankDisplay = section.getString(key + ".display", color + displayName)
                    .replace("%name%", displayName)
                    .replace("%color%", color);
            int weight = section.getInt(key + ".weight", 100);
            ranks.put(normalized, new RankInfo(prefix, rankDisplay, weight));
        }
        return ranks;
    }

    private RankInfo getRankInfo(String rankKey) {
        Map<String, RankInfo> ranks = loadRanks();
        String normalized = rankKey == null ? "" : rankKey.toLowerCase(Locale.ROOT);
        RankInfo rankInfo = ranks.get(normalized);
        if (rankInfo != null) {
            return rankInfo;
        }
        return ranks.values().stream().findFirst().orElse(new RankInfo("&7[Member] ", "&7Member", 100));
    }

    private String getSidebarTitle(Player viewer) {
        String raw = plugin.getConfig().getString("sidebar-title", "&6&l%server_name%");
        return replacePlaceholders(viewer, raw);
    }

    private String toDisplayName(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "Member";
        }
        String lower = key.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        String[] parts = lower.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.trim().isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.length() == 0 ? "Member" : out.toString();
    }

    private static final class RankInfo {
        private final String prefix;
        private final String displayName;
        private final int weight;

        private RankInfo(String prefix, String displayName, int weight) {
            this.prefix = prefix;
            this.displayName = displayName;
            this.weight = weight;
        }

        private String getPrefix() {
            return prefix;
        }

        private String getDisplayName() {
            return displayName;
        }

        private int getWeight() {
            return weight;
        }
    }
}
