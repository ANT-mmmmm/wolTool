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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public final class Wol_tool extends JavaPlugin {
    private FileConfiguration config;


    private static Wol_tool plugin;

    @Override
    public void onEnable() {
        plugin = this;

        Bukkit.getLogger().info("WOL_tool has been enabled");
        this.saveDefaultConfig();
        config = this.getConfig();

        // 注册命令
        Objects.requireNonNull(this.getCommand("wol")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("ping")).setExecutor(this); // 新增 /ping 命令注册

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
    }

    @Override
    public void onDisable() {
        plugin = null;
        Bukkit.getLogger().info("WOL_tool has been disabled");
    }

    /**
     * 处理命令发送者发送的命令
     *
     * @param sender  命令发送者
     * @param command 被执行的命令对象
     * @param label   命令别名
     * @param args    命令参数数组
     * @return true 如果命令被成功处理，否则返回 false
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 检查发送者是否有权限
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        // 处理 "wol" 命令
        if (command.getName().equalsIgnoreCase("wol")) {
            String computerName;
            // 判断是否提供了计算机名称参数
            if (args.length < 1) {
                computerName = config.getString("default_computer");
                // 检查默认计算机配置
                if (computerName == null || computerName.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No default computer configured.");
                    return true;
                }
            } else {
                computerName = args[0];
            }

            // 获取计算机的 MAC 地址
            String macAddress = config.getString("mac-addresses." + computerName);
            // 检查 MAC 地址是否有效
            if (macAddress == null || macAddress.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Could not find MAC address for computer: " + computerName);
                sender.sendMessage(ChatColor.RED + "Available computers: " + String.join(", ",
                        config.getConfigurationSection("mac-addresses").getKeys(false)));


                return true;
            }

            // 发送 Wake-On-LAN 命令
            sendWakeOnLanCommand(sender, computerName, macAddress);
            return true;
        }

        // 处理 "ping" 命令
        if (command.getName().equalsIgnoreCase("ping")) {
            // 检查命令参数
            String ipAddress = "127.0.0.1";
            if (args.length < 1) {
                ipAddress = config.getString("ip-addresses." + config.getString("default_computer"));
            } else {
                ipAddress = args[0];
            }


            String finalIpAddress = ipAddress;
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                boolean isOnline = pingIpAddress(finalIpAddress);

                // 回主线程发送消息
                Bukkit.getScheduler().runTask(this, () -> {
                    if (isOnline) {
                        sender.sendMessage(ChatColor.GREEN + "Ping to " + finalIpAddress + " succeeded.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Ping to " + finalIpAddress + " failed.");
                    }
                });
            });


            return true;
        }
        // 处理 "goto" 命令
        if (command.getName().equalsIgnoreCase("goto")) {
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /goto <player>");
                return true;
            }

            Player targetPlayer = Bukkit.getPlayer(args[0]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sender.sendMessage(ChatColor.RED + "Player not found or is offline.");
                return true;
            }

            handleGotoInternal(targetPlayer);
            return true;
        }
        // 如果命令未被处理，返回 false
        return false;
    }

    private void handleGotoInternal(Player player) {
        String defaultComputer = config.getString("default_computer");
        getLogger().info("handleGotoInternal");
        getLogger().info("defaultComputer: " + defaultComputer);
        getLogger().info("ipAddress: " + config.getString("ip-addresses." + defaultComputer));
        getLogger().info("macAddress: " + config.getString("mac-addresses." + defaultComputer));
        if (defaultComputer == null || defaultComputer.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No default computer configured.");
            return;
        }

        String ipAddress = config.getString("ip-addresses." + defaultComputer);
        if (ipAddress == null || ipAddress.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No IP address configured for the default computer.");
            return;
        }


        // 异步执行 ping 检查
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean isOnline = pingIpAddress(ipAddress);

            // 回主线程执行后续操作
            Bukkit.getScheduler().runTask(this, () -> {
                if (isConnectPluginLoaded()) {
                    if (isOnline) {
                        for (Player player_ : Bukkit.getOnlinePlayers()) {
                            getLogger().info("player_: " + player_.getName());
                            player.sendMessage(ChatColor.GREEN + "正在连接服务器...");
                            Bukkit.getScheduler().runTaskLater(Wol_tool.this, () -> {
                                for (int i = 10; i > 0; i--) {
                                    int titleCountdown = i;
                                    Bukkit.getScheduler().runTaskLater(Wol_tool.this, () -> {
                                        player.sendTitle("", ChatColor.RED + String.valueOf(titleCountdown), 10, 20, 10);
                                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                                    }, (10 - titleCountdown) * 20L);
                                }
                            }, 0L);

                            Bukkit.getScheduler().runTaskLater(this, () -> {
                                Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "connect " + player_.getName() + " " + defaultComputer);
                            }, 20L);
                        }
                    } else {
// 获取配置中的多行消息
                        List<String> messages = config.getStringList("login-messages");

// 如果没有设置，则使用默认值（可选）


// 发送每条消息
                        for (String line : messages) {
                            player.sendMessage(ChatColor.WHITE + line);
                        }
                        startMachineConnectionCountdown(defaultComputer);
                    }
                } else {
                    logMissingDependencyAndKick(player);
                }
            });
        });
    }

    // 检查 Connect 插件是否加载
    private boolean isConnectPluginLoaded() {
        return Bukkit.getServer().getPluginManager().getPlugin("Connect") != null;
    }

    // 执行系统 ping 命令检测服务器状态

    /**
     * 执行 ping 检查，并根据配置的超时时间终止长时间未响应的 ping 进程。
     *
     * @param ipAddress 要 ping 的 IP 地址
     * @return 如果 ping 成功返回 true，否则返回 false
     */
    private boolean pingIpAddress(String ipAddress) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "1", ipAddress);
            processBuilder.redirectErrorStream(true); // 合并错误流和标准流

            Process process = processBuilder.start();

            // 使用配置中的超时时间（默认 3000 毫秒）
            boolean finished = process.waitFor(config.getInt("ping_timeout", 200), java.util.concurrent.TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroy(); // 超时后终止进程
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
            return false;
        }
        return false;
    }


    /**
     * 启动一个15秒的倒计时并广播显示，倒计时结束后连接所有玩家到指定服务器。
     *
     * @param machineName 要连接的目标服务器名称
     */
    private void startMachineConnectionCountdown(String machineName) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        AtomicInteger countdown = new AtomicInteger(60); // 倒计时秒数

        // 先定义 task 变量
        final BukkitTask[] taskInstance = new BukkitTask[1]; // 使用数组来允许 final 的可变引用

        // 定义倒计时任务
        Runnable task = () -> {
            // 每次刷新倒计时都异步执行 ping 检查
            String ipAddress = config.getString("ip-addresses." + machineName);
            boolean isOnline = pingIpAddress(ipAddress);

            if (isOnline) {
                // 如果 ping 通，立即连接玩家并取消倒计时任务
                Bukkit.getScheduler().runTask(Wol_tool.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Bukkit.getScheduler().runTaskLater(Wol_tool.this, () -> {
                            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "connect " + player.getName() + " " + machineName);
                        }, 60L); // 延迟 60 ticks (3 seconds)
                        player.sendMessage(ChatColor.GREEN + "服务器已就绪！");
                        player.sendMessage(ChatColor.YELLOW + "倒计时开始：5 秒后连接...");
                        
                        // 设置倒计时任务
                        Bukkit.getScheduler().runTaskLater(Wol_tool.this, () -> {
                            for (int i = 10; i > 0; i--) {
                                int titleCountdown = i;
                                Bukkit.getScheduler().runTaskLater(Wol_tool.this, () -> {
                                    player.sendTitle("", ChatColor.RED + String.valueOf(titleCountdown), 10, 20, 10);
                                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                                }, (10 - titleCountdown) * 20L); // 每秒触发一次
                            }
                        }, 0L);

                    }

                    Bukkit.getLogger().info("Server is online. Players have been connected immediately.");
                    if (taskInstance[0] != null) {
                        taskInstance[0].cancel(); // 取消倒计时任务
                    }
                });
                return;
            }

            if (countdown.get() <= 0) {
                // 倒计时结束，在主线程执行最终命令
                Bukkit.getScheduler().runTask(Wol_tool.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "connect " + player.getName() + " " + machineName);
                        player.sendMessage(ChatColor.YELLOW + "倒计时结束，强制连接到服务器");
                    }

                    Bukkit.getLogger().info("Countdown ended. All players connected to server: " + machineName);
                    Bukkit.getScheduler().runTaskLater(Wol_tool.this, () -> {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kick " + player.getName() + " 你在意外的位置");
                        }
                    }, 20L); // 延迟 20 ticks (1 second)

                    // 取消当前任务
                    if (taskInstance[0] != null) {
                        taskInstance[0].cancel();
                    }
                });
            } else {
                // 每秒更新一次倒计时信息
                Bukkit.getScheduler().runTask(Wol_tool.this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        // 发送倒计时标题
                        player.sendTitle("", "等待 " + ChatColor.RED + countdown + "s " + ChatColor.WHITE + "连接到服务器", 2, 20, 2);
                        sendWakeOnLanCommand(player, machineName, config.getString("mac-addresses." + machineName));

                        // 随机粒子效果
                        ChatColor[] colors = {ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW, ChatColor.GREEN, ChatColor.BLUE, ChatColor.LIGHT_PURPLE};
                        ChatColor color = colors[new Random().nextInt(colors.length)];

                        player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

                        try {
                            Color particleColor = Color.fromRGB(
                                    new Random().nextInt(256),
                                    new Random().nextInt(256),
                                    new Random().nextInt(256)
                            );
                            player.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 30, 0.5F, 0.5F, 0.5F, 0.1F, new Particle.DustOptions(particleColor, 1));
                        } catch (Exception ignored) {}
                    }
                });
                countdown.getAndDecrement();
            }
        };

        // 异步启动倒计时任务，并赋值给 taskInstance
        taskInstance[0] = scheduler.runTaskTimerAsynchronously(this, task, 0L, 20L); // 20 ticks = 1 second
    }


    // 记录日志并踢出玩家
    private void logMissingDependencyAndKick(Player player) {
        Bukkit.getLogger().warning("Connect plugin not found. Cannot execute /connect.");
        player.kickPlayer("服务器缺少必要插件依赖。");
    }


    public class PlayerJoinListener implements Listener {

        @EventHandler
        public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
            Player player = event.getPlayer();

            // 确保玩家已完全连接后再发送标题
            Bukkit.getScheduler().runTaskLater(Wol_tool.this, () -> {
                player.sendTitle("", "等待连接到服务器", 2, 20, 2);
            }, 10L); // 延迟 1 秒后发送标题，确保连接完成

            getLogger().info(Boolean.toString(config.getBoolean("autowake", false)));

            if (config.getBoolean("autowake", false)) {
                getLogger().info("Player " + player.getName() + " joined. Checking Wake-On-LAN status.");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "goto "+event.getPlayer().getName());
            }
        }
    }


    // 调用系统命令发送 WOL
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
            Bukkit.getLogger().log(java.util.logging.Level.SEVERE, ChatColor.RED + "Error while sending WOL command: " + e.getMessage());
        }
    }


}