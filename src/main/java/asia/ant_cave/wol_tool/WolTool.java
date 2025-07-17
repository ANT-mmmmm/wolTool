package asia.ant_cave.wol_tool;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class WolTool extends JavaPlugin {
    private FileConfiguration pluginConfig;
    private static WolTool plugin;

    // 常量定义
    private static final long DELAY_60_TICKS = 60L;
    private static final long DELAY_20_TICKS = 20L;
    private static final int PING_TIMEOUT_DEFAULT = 200;
    private static final String DEFAULT_IP = "127.0.0.1";

    @Override
    public void onEnable() {
        plugin = this;
        Bukkit.getLogger().info("WOL_tool has been enabled");

        // 配置文件初始化
        if (!getDataFolder().exists() || !new java.io.File(getDataFolder(), "config.yml").exists()) {
            this.saveDefaultConfig();
        }
        pluginConfig = this.getConfig();

        // 命令注册
        Objects.requireNonNull(this.getCommand("wol")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("ping")).setExecutor(this);

        // 事件监听器注册
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
    }

    @Override
    public void onDisable() {
        plugin = null;
        Bukkit.getLogger().info("WOL_tool has been disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 权限检查
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // 命令处理
        if (command.getName().equalsIgnoreCase("wol")) {
            handleWolCommand(sender, args);
            return true;
        }

        if (command.getName().equalsIgnoreCase("ping")) {
            handlePingCommand(sender, args);
            return true;
        }

        if (command.getName().equalsIgnoreCase("goto")) {
            handleGotoCommand(sender, args);
            return true;
        }

        return false;
    }

    // 处理WOL命令
    private void handleWolCommand(CommandSender sender, String[] args) {
        String computerName = getComputerName(args);

        String macAddress = pluginConfig.getString("mac-addresses." + computerName);
        if (macAddress == null || macAddress.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Could not find MAC address for computer: " + computerName);
            sender.sendMessage(ChatColor.RED + "Available computers: " + String.join(", ",
                    pluginConfig.getConfigurationSection("mac-addresses").getKeys(false)));
            return;
        }

        sendWakeOnLanCommand(sender, computerName, macAddress);
    }

    // 获取计算机名称
    private String getComputerName(String[] args) {
        if (args.length < 1) {
            String defaultComputer = pluginConfig.getString("default_computer");
            if (defaultComputer == null || defaultComputer.isEmpty()) {
                throw new IllegalArgumentException("No default computer configured");
            }
            return defaultComputer;
        }
        return args[0];
    }

    // 处理Ping命令
    private void handlePingCommand(CommandSender sender, String[] args) {
        String ipAddress = args.length < 1
            ? pluginConfig.getString("ip-addresses." + pluginConfig.getString("default_computer"))
            : args[0];

        String finalIpAddress = ipAddress;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean isOnline = pingIpAddress(finalIpAddress);

            Bukkit.getScheduler().runTask(this, () -> {
                if (isOnline) {
                    sender.sendMessage(ChatColor.GREEN + "Ping to " + finalIpAddress + " succeeded.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Ping to " + finalIpAddress + " failed.");
                }
            });
        });
    }

    // 处理Goto命令
    private void handleGotoCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /goto <player>");
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found or is offline.");
            return;
        }

        handleGotoInternal(targetPlayer);
    }

    // 内部Goto处理
    private void handleGotoInternal(Player player) {
        String defaultComputer = pluginConfig.getString("default_computer");
        if (defaultComputer == null || defaultComputer.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No default computer configured.");
            return;
        }

        String ipAddress = pluginConfig.getString("ip-addresses." + defaultComputer);
        if (ipAddress == null || ipAddress.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No IP address configured for the default computer.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean isOnline = pingIpAddress(ipAddress);

            Bukkit.getScheduler().runTask(this, () -> {
                if (isConnectPluginLoaded()) {
                    if (isOnline) {
                        connectPlayers(player, defaultComputer);
                    } else {
                        sendLoginMessages(player);
                        startMachineConnectionCountdown(defaultComputer);
                    }
                } else {
                    logMissingDependencyAndKick(player);
                }
            });
        });
    }

    // 检查插件依赖
    private boolean isConnectPluginLoaded() {
        return Bukkit.getServer().getPluginManager().getPlugin("Connect") != null;
    }

    // 执行Ping检查
    private boolean pingIpAddress(String ipAddress) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "1", ipAddress);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            boolean finished = process.waitFor(PING_TIMEOUT_DEFAULT, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroy();
                return false;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("bytes from")) {
                    return true;
                }
            }
        } catch (IOException | InterruptedException e) {
            Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                ChatColor.RED + "Ping execution error: " + e.getMessage(), e);
            return false;
        }
        return false;
    }

    // 倒计时连接服务器
    private void startMachineConnectionCountdown(String machineName) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        AtomicInteger countdown = new AtomicInteger(60);

        final BukkitTask[] taskInstance = new BukkitTask[1];
        Runnable task = () -> {
            String ipAddress = pluginConfig.getString("ip-addresses." + machineName);
            boolean isOnline = pingIpAddress(ipAddress);

            if (isOnline) {
                Bukkit.getScheduler().runTask(WolTool.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        connectPlayer(player, machineName, DELAY_60_TICKS);
                        sendReadyMessages(player);
                    }
                    if (taskInstance[0] != null) taskInstance[0].cancel();
                });
                return;
            }

            if (countdown.get() <= 0) {
                Bukkit.getScheduler().runTask(WolTool.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        connectPlayer(player, machineName, DELAY_20_TICKS);
                        player.sendMessage(ChatColor.YELLOW + "倒计时结束，强制连接到服务器");
                    }
                    if (taskInstance[0] != null) taskInstance[0].cancel();
                });
            } else {
                Bukkit.getScheduler().runTask(WolTool.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        sendCountdownTitle(player, countdown.get());
                        sendWakeOnLanCommand(player, machineName,
                            pluginConfig.getString("mac-addresses." + machineName));
                        spawnParticles(player);
                    }
                });
                countdown.getAndDecrement();
            }
        };

        taskInstance[0] = scheduler.runTaskTimerAsynchronously(this, task, 0L, DELAY_20_TICKS);
    }

    // 发送玩家连接命令
    private void connectPlayers(Player player, String computer) {
        for (Player player_ : Bukkit.getOnlinePlayers()) {
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(),
                "connect " + player_.getName() + " " + computer);
            player.sendMessage(ChatColor.GREEN + "服务器已就绪！");
        }
    }

    // 玩家连接方法
    private void connectPlayer(Player player, String machineName, long delay) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(),
                "connect " + player.getName() + " " + machineName);
        }, delay);
    }

    // 发送准备消息
    private void sendReadyMessages(Player player) {
        player.sendMessage(ChatColor.GREEN + "服务器已就绪！");
        player.sendMessage(ChatColor.YELLOW + "倒计时开始：5 秒后连接...");
    }

    // 发送登录消息
    private void sendLoginMessages(Player player) {
        List<String> messages = pluginConfig.getStringList("login-messages");
        for (String line : messages) {
            player.sendMessage(ChatColor.WHITE + line);
        }
    }

    // 发送倒计时标题
    private void sendCountdownTitle(Player player, int seconds) {
        player.sendTitle("", "等待 " + ChatColor.RED + seconds + "s " + ChatColor.WHITE + "连接到服务器", 2, 20, 2);
    }

    // 生成粒子效果
    private void spawnParticles(Player player) {
        ChatColor[] colors = {
            ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW,
            ChatColor.GREEN, ChatColor.BLUE, ChatColor.LIGHT_PURPLE
        };
        ChatColor color = colors[new java.util.Random().nextInt(colors.length)];

        player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

        try {
            Color particleColor = Color.fromRGB(
                new java.util.Random().nextInt(256),
                new java.util.Random().nextInt(256),
                new java.util.Random().nextInt(256)
            );
            player.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0),
                30, 0.5F, 0.5F, 0.5F, 0.1F, new Particle.DustOptions(particleColor, 1));
        } catch (Exception ignored) {}
    }

    // 记录日志并踢出玩家
    private void logMissingDependencyAndKick(Player player) {
        Bukkit.getLogger().warning("Connect plugin not found. Cannot execute /connect.");
        player.kickPlayer("服务器缺少必要插件依赖。");
    }

    // 玩家加入监听器
    public class PlayerJoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
            Player player = event.getPlayer();

            Bukkit.getScheduler().runTaskLater(WolTool.this, () -> {
                player.sendTitle("", "等待连接到服务器", 2, 20, 2);
            }, DELAY_20_TICKS);

            if (pluginConfig.getBoolean("autowake", false)) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "goto " + event.getPlayer().getName());
            }
        }
    }

    // 发送WOL命令
    private void sendWakeOnLanCommand(CommandSender sender, String computerName, String macAddress) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("wakeonlan", macAddress);
            Process process = processBuilder.start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                Bukkit.getLogger().info(ChatColor.GREEN + "Successfully sent WOL command.");
            } else {
                Bukkit.getLogger().warning(ChatColor.RED + "Failed to send WOL command. Exit code: " + exitCode);
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                ChatColor.RED + "Error while sending WOL command: " + e.getMessage(), e);
        }
    }
}
