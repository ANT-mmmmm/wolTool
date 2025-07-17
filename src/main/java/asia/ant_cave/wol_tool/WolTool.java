package asia.ant_cave.wol_tool;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class WolTool extends JavaPlugin implements Listener {
    private FileConfiguration pluginConfig;
    private static WolTool plugin;

    // 常量定义
    private static final long CONNECT_DELAY = 20*10L;
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


        // 获取插件管理器并注册事件监听器
        PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.registerEvents(this, this);

        // 注册BungeeCord通信通道
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
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

        if (command.getName().equalsIgnoreCase("reload")) {
            reloadConfig();
            pluginConfig = this.getConfig();
            sender.sendMessage(ChatColor.GREEN + "Config reloaded.");
        }

        if (command.getName().equalsIgnoreCase("send")) {

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
                if (isOnline) {
                    connectPlayers(player, defaultComputer);
                } else {
                    sendLoginMessages(player);
                    startMachineConnectionCountdown(defaultComputer);
                }
            });
        });
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
    /**
     * 启动机器连接倒计时并定期检查服务器状态
     * 流程：
     * 1. 初始化倒计时和任务调度器
     * 2. 每隔 CONNECT_DELAY 时间 ping 一次目标 IP
     * 3. 如果服务器在线：
     *    - 向所有玩家发送准备消息
     *    - 调用 connectPlayer 开始连接流程
     *    - 取消定时任务
     * 4. 如果倒计时结束仍未上线：
     *    - 强制执行连接操作
     *    - 发送提示信息
     *    - 取消定时任务
     * 5. 如果仍在等待中：
     *    - 发送倒计时标题
     *    - 发送 WOL 唤醒命令
     *    - 生成粒子效果
     *    - 倒计时减一
     *
     * @param machineName 目标机器名称，用于查找配置中的 IP 和 MAC 地址
     */
    private void startMachineConnectionCountdown(String machineName) {
        // 获取 Bukkit 的调度器，用于创建定时任务
        BukkitScheduler scheduler = Bukkit.getScheduler();
        
        // 设置初始倒计时为 60 秒
        AtomicInteger countdown = new AtomicInteger(60);

        // 使用数组来保存任务实例以便在任务内部取消它
        final BukkitTask[] taskInstance = new BukkitTask[1];

        // 创建异步的 Runnable 任务
        Runnable task = () -> {
            // 获取目标机器的 IP 地址
            String ipAddress = pluginConfig.getString("ip-addresses." + machineName);
            
            // 检查 IP 是否在线
            boolean isOnline = pingIpAddress(ipAddress);

            // 如果服务器已上线
            if (isOnline) {
                // 切换回主线程执行以下操作
                Bukkit.getScheduler().runTask(WolTool.this, () -> {
                    // 遍历所有在线玩家
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        // 连接玩家到目标服务器
                        connectPlayer(player, machineName, CONNECT_DELAY);
                        // 发送就绪消息
                        sendReadyMessages(player);
                    }
                    // 如果任务存在则取消它
                    if (taskInstance[0] != null) taskInstance[0].cancel();
                });
                return;
            }

            // 如果倒计时结束
            if (countdown.get() <= 0) {
                // 切换回主线程执行以下操作
                Bukkit.getScheduler().runTask(WolTool.this, () -> {
                    // 遍历所有在线玩家
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        // 强制连接玩家到目标服务器
                        connectPlayer(player, machineName, CONNECT_DELAY);
                        // 发送强制连接提示
                        player.sendMessage(ChatColor.YELLOW + "倒计时结束，强制连接到服务器");
                    }
                    // 如果任务存在则取消它
                    if (taskInstance[0] != null) taskInstance[0].cancel();
                });
            } else {
                // 如果仍在等待中，切换回主线程执行以下操作
                Bukkit.getScheduler().runTask(WolTool.this, () -> {
                    // 遍历所有在线玩家
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        // 发送倒计时标题
                        sendCountdownTitle(player, countdown.get());
                        // 发送 WOL 唤醒命令
                        sendWakeOnLanCommand(player, machineName,
                                pluginConfig.getString("mac-addresses." + machineName));
                        // 生成粒子效果
                        spawnParticles(player);
                    }
                });
                // 倒计时减一
                countdown.getAndDecrement();
            }
        };

        // 使用异步调度器启动定时任务，每 CONNECT_DELAY ticks 执行一次
        taskInstance[0] = scheduler.runTaskTimerAsynchronously(this, task, 0L, 20L);
    }

    // 发送玩家连接命令
    private void connectPlayers(Player player, String computer) {
        for (Player player_ : Bukkit.getOnlinePlayers()) {
            connectPlayer(player, computer,  CONNECT_DELAY);
            player.sendMessage(ChatColor.GREEN + "服务器已就绪！");
        }
    }

    // 玩家连接方法
    private static final long TICKS_PER_SECOND = 20L;  // 符合常量规范
    private static final long DELAY_TASK_INTERVAL = TICKS_PER_SECOND * 1;  // 每两秒执行一次

    /**
     * 连接玩家到目标服务器，带倒计时提示、粒子效果和延迟执行
     *
     * @param player      要连接的玩家
     * @param machineName 目标服务器名称
     * @param delay       延迟时间（以 tick 为单位）
     */
    private void connectPlayer(Player player, String machineName, long delay) {
        // 将延迟时间从 tick 转换为秒
        long totalSeconds = delay / TICKS_PER_SECOND;

        // 创建一个final数组来保存totalSeconds值，以便在lambda表达式中使用
        final long[] finalTotalSeconds = { totalSeconds };

        // 创建一个定时任务，每秒更新一次倒计时标题
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (finalTotalSeconds[0] >= 0) {
                // 发送倒计时标题给玩家
                player.sendTitle("§a连接将在 §d" + finalTotalSeconds[0] + "秒 §6后建立", "§a稍安勿躁 请勿退出", 0, (int) DELAY_TASK_INTERVAL, 0);

                // 在倒计时期间生成粒子效果
                spawnParticles(player);

                // 在最后一秒播放音效
                if (finalTotalSeconds[0] == 1) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }

                finalTotalSeconds[0]--;  // 减少剩余秒数
            }
        }, 0L, DELAY_TASK_INTERVAL);  // 每秒执行一次

        // 在指定延迟后执行连接操作
        Bukkit.getScheduler().runTaskLater(this, () -> {
            sendToServer(player, machineName);  // 将玩家发送到目标服务器
        }, delay - TICKS_PER_SECOND);  // 确保在倒计时结束前执行
    }


    // 发送准备消息
    private void sendReadyMessages(Player player) {
        player.sendMessage(ChatColor.GREEN + "服务器已就绪！");
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
        } catch (Exception ignored) {
        }
    }



    // 玩家加入监听器
    public class PlayerJoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
            Player player = event.getPlayer();

            Bukkit.getScheduler().runTaskLater(WolTool.this, () -> {
                player.sendTitle("", "等待连接到服务器", 2, 20, 2);
            }, CONNECT_DELAY);

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


    /**
     * 将指定玩家发送到目标服务器
     *
     * @param player 要发送的玩家
     * @param targetServer 目标服务器名称
     */
    // 在类中定义常量
    private static final String BUNGEECORD_COMMAND_CONNECT = "Connect";

    public static void sendToServer(Player player, String targetServer) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            // 写入BungeeCord指令类型
            dataOutputStream.writeUTF(BUNGEECORD_COMMAND_CONNECT);
            // 写入目标服务器名称
            dataOutputStream.writeUTF(targetServer);
        } catch (IOException e) {
            Bukkit.getLogger().log(java.util.logging.Level.SEVERE,
                    ChatColor.RED + "发送连接命令时发生IO异常: " + e.getMessage(), e);
        }

        // 发送插件消息到指定服务器
        player.sendPluginMessage(plugin, "BungeeCord", byteArrayOutputStream.toByteArray());
    }


}
