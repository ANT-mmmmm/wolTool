
# 🌟WOL Tool - Wake-on-LAN Minecraft Plugin

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green) ![Java](https://img.shields.io/badge/Java-21-blue) ![SpigotMC](https://img.shields.io/badge/Framework-SpigotMC-orange)

## 📦 Project Introduction

This plugin provides **Wake-on-LAN (WOL)** functionality for Minecraft servers, allowing remote awakening of devices on the local network through in-game commands. It's particularly useful for scenarios where physical servers or computers need to be controlled from the Minecraft game interface.

## 🔧 Key Features

- 🌐 **WOL Wakeup**: Send Magic Packets to wake up specified devices through in-game commands
- 🖥️ **Ping Detection**: Detect if target devices are online
- ⚙️ **Configuration Management**: Support configuration of MAC addresses, IP addresses and other parameters via [config.yml](file://E:\Users\Admin\Desktop\java_project\java_project\wol_tool\wolTool\src\main\resources\config.yml)
- 🕒 **Automatic Countdown Connection**: Automatically detect and connect to target servers after device wakeup
- 💬 **Messaging**: Provide rich in-game hints and particle effects

## 🛠️ Tech Stack

- 🗄️ Build Tool: Gradle
- 💻 Programming Language: Java 21
- 🎮 Game Framework: SpigotMC API 1.21.4-R0.1-SNAPSHOT

## 📁 File Structure

```

wolTool/
├── src/
│   └── main/
│       ├── java/                  # Java source code
│       │   └── asia/ant_cave/wol_tool/
│       │       └── WolTool.java   # Main plugin class
│       └── resources/             # Resource files
│           ├── config.yml         # Main configuration file
│           └── plugin.yml         # Plugin metadata
├── README.md                      # Project documentation
└── gradle.properties              # Gradle configuration properties
```
## 🧪 Usage

### Installation Steps

1. 📥 Download or build the plugin JAR file
2. 📁 Place the JAR file into the SpigotMC server's `plugins/` directory
3. ▶️ Start the server to generate configuration files
4. ✏️ Edit `plugins/WOL/config.yml` to add your device information
5. 🔁 Reload or restart the server

### Configuration ([config.yml](file://E:\Users\Admin\Desktop\java_project\java_project\wol_tool\wolTool\src\main\resources\config.yml))

```yaml
# Config.yml content from your project

# Default computer identifier used in WOL operations
default_computer: computer1

# Whether to automatically wake up the default computer on server startup
autowake: true

# MAC addresses of target devices
mac-addresses:
  computer1: "00:11:22:33:44:55"  # Example MAC address format

# IP addresses of target devices
ip-addresses:
  computer1: "192.168.1.1"  # Local network IP address for device communication

# Timeout duration (in milliseconds) for IP connectivity tests
ip-test-timeout: 200

# Login messages displayed to players upon joining the server
login-messages:
  - "欢迎来到服务器！"
  - "正在准备连接..."
``` 
### In-game Commands

| Command | Function | Example |
|-------|---------|--------|
| `/wol [computer]` | Send WOL wake command | `/wol computer1` |
| `/ping [ip]` | Check if target device is online | `/ping 192.168.1.1` |
| `/goto <player>` | Connect player to default server | `/goto Steve` |
| `/reload` | Reload configuration file | `/reload` |

## 🧱 Development Standards

- 📝 All public methods include complete Javadoc comments
- 🧮 Time unit handling follows standard game tick conversion (20 ticks per second)
- 🔄 Use BukkitScheduler to implement periodic tasks and delayed execution
- 📦 Configuration access encapsulated in utility methods to improve testability
- 📋 Exception handling includes detailed logging

## 🧰 Developer Guide

### Build Project

```
bash
# Build the plugin
gradle build
```
### Runtime Environment

- JDK 21
- SpigotMC 1.21.4 server
- Network environment supporting Wake-on-LAN
---
# 🌟WOL 工具 - Wake-on-LAN Minecraft 插件

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green) ![Java](https://img.shields.io/badge/Java-21-blue) ![SpigotMC](https://img.shields.io/badge/Framework-SpigotMC-orange)

## 📦 项目简介

这个插件为 Minecraft 服务器提供 **Wake-on-LAN (WOL)** 功能，允许通过游戏内命令远程唤醒本地网络中的设备。它特别适用于需要从 Minecraft 游戏界面控制物理服务器或计算机的场景。

## 🔧 主要功能

- 🌐 **WOL 唤醒**：通过插件发送 Magic Packet 唤醒指定设备
- 🖥️ **Ping 检测**：检测目标设备是否在线
- ⚙️ **配置管理**：支持通过 [config.yml](file://E:\Users\Admin\Desktop\java_project\java_project\wol_tool\wolTool\src\main\resources\config.yml) 配置 MAC 地址、IP 地址和其他参数
- 🕒 **自动倒计时连接**：在设备唤醒后自动检测并连接到目标服务器
- 💬 **消息提示**：提供丰富的游戏内提示和粒子效果

## 🛠️ 技术栈

- 🗄️ 构建工具: Gradle
- 💻 编程语言: Java 21
- 🎮 游戏框架: SpigotMC API 1.21.4-R0.1-SNAPSHOT

## 📁 文件结构

```

wolTool/
├── src/
│   └── main/
│       ├── java/                  # Java 源代码
│       │   └── asia/ant_cave/wol_tool/
│       │       └── WolTool.java   # 插件主类
│       └── resources/             # 资源文件
│           ├── config.yml         # 主配置文件
│           └── plugin.yml         # 插件元数据
├── README.md                      # 项目说明文档
└── gradle.properties              # Gradle 配置属性
```
## 🧪 使用方法

### 安装步骤

1. 📥 下载或构建插件 JAR 文件
2. 📁 将 JAR 文件放入 SpigotMC 服务器的 `plugins/` 目录
3. ▶️ 启动服务器以生成配置文件
4. ✏️ 编辑 `plugins/WOL/config.yml` 添加你的设备信息
5. 🔁 重新加载或重启服务器

### 配置说明 ([config.yml](file://E:\Users\Admin\Desktop\java_project\java_project\wol_tool\wolTool\src\main\resources\config.yml))

```
yaml
# Config.yml 内容与您项目中的一致
default_computer: computer1
autowake: true
mac-addresses:
computer1: "00:11:22:33:44:55" # 设备的 MAC 地址
ip-addresses:
computer1: "192.168.1.1"       # 设备的 IP 地址
ip-test-timeout: 200              # Ping 测试超时时间（毫秒）

login-messages:
  - "欢迎来到服务器！"
  - "正在准备连接..."
```
### 游戏内命令

| 命令 | 功能 | 示例 |
|------|------|------|
| `/wol [computer]` | 发送 WOL 唤醒命令 | `/wol computer1` |
| `/ping [ip]` | 检查目标设备是否在线 | `/ping 192.168.1.1` |
| `/goto <player>` | 让玩家连接到默认服务器 | `/goto Steve` |
| `/reload` | 重新加载配置文件 | `/reload` |

## 🧱 开发规范

- 📝 所有公共方法都包含完整的 Javadoc 注释
- 🧮 时间单位处理遵循标准游戏刻换算（每秒 20 ticks）
- 🔄 使用 BukkitScheduler 实现周期性任务和延迟执行
- 📦 配置访问封装在工具方法中以提高可测试性
- 📋 异常处理包含详细的日志记录

## 🧰 开发者指南

### 构建项目

```
bash
# 构建插件
gradle build
```
### 运行环境

- JDK 21
- SpigotMC 1.21.4 服务端
- 支持 Wake-on-LAN 的网络环境
