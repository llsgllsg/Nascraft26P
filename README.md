<div align="center">

# Nascraft26P

**玩家驱动的 Minecraft 动态市场经济 · 汉化增强版**

买卖塑造价格，价格塑造策略。

[![Release](https://img.shields.io/github/v/release/llsgllsg/Nascraft26P?style=flat-square&label=Release&color=2ea44f)](https://github.com/llsgllsg/Nascraft26P/releases)
[![Build](https://github.com/llsgllsg/Nascraft26P/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/llsgllsg/Nascraft26P/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/llsgllsg/Nascraft26P?style=flat-square&color=3b82f6)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-f89820?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Paper](https://img.shields.io/badge/Paper-1.21.11%20%7C%2026.2%20%7C%2026.3-30a2ff?style=flat-square)](https://papermc.io)

[安装](#安装) · [功能](#功能) · [命令](#命令与权限) · [构建](#从源码构建) · [常见问题](#常见问题)

</div>

---

> **Nascraft26P** 是基于 [Nascraft](https://www.spigotmc.org/resources/108216/) 的汉化 + 兼容性维护版本，跟随 Paper 最新稳定版更新，当前支持 **1.21.11 / 26.2 / 26.3**，内置完整中文语言包。

## 功能

| 功能 | 说明 |
|------|------|
| 动态价格 | 价格随买卖量实时波动，带税制、噪声、支撑位 / 压力位与价格弹性 |
| 完整交易链路 | 主菜单、分类页、热门趋势、涨跌幅榜，支持买入 / 卖出 / 批量出售 / 出售魔杖 |
| 限价单 | 挂单交易，支持超时与超买 / 超卖限制 |
| 资产组合 | 组合扩容、按比例联动的子物品、资产榜单 |
| 借贷系统 | 以资产组合为抵押，利息、清算与违约处理 |
| 跨服市场 | SQLite 单服 / MySQL 多服共享，Redis 实时同步 |
| Discord 桥接 | 交易日志、账号绑定、价格提醒推送 |
| QQ 桥接（OneBot v11） | 交易日志、行情查询、价格提醒、账号绑定；兼容个人 QQ 与 QQ 官方机器人 |
| 自托管网页 | 行情、K 线图表、会话登录 |
| 中文体验 | zh_CN 语言包、汉化配置注释、汉化的管理员编辑器 |

## 兼容性

| 构建目标 | 服务端 | Java | 界面库 | 状态 |
|----------|--------|------|--------|------|
| `1.21.11` | Paper / Purpur 1.21.11 | 21 | invui 1.x | √ 支持 |
| `26.2` | Paper / Purpur 26.2 | 25 | invui 2.3.0 | √ 支持 |
| `26.3` | Paper / Purpur 26.3 | 25 | invui 2.5.1 | √ 支持（推荐） |
| `26.4` | — | — | — | × 等待 Paper 发布 26.4 API |

[常见问题](#常见问题)。

## 安装

### 环境要求

- **Paper / Purpur 1.21.11、26.2 或 26.3**（Folia 亦已声明支持）
- **Vault**（必装，作为默认货币）
- 可选：**PlaceholderAPI**（占位符 / 多货币）、**AdvancedGUI**（游戏内图表）
- 跨服：MySQL 8.0+ / MariaDB 10.2+ 与 Redis；Discord：JDA（随插件加载）；QQ：OneBot v11 实现（NapCat / LLOneBot / Lagrange 等，可选）

### 步骤

1. 从 [Releases](https://github.com/llsgllsg/Nascraft26P/releases) 下载与你服务端版本对应的 jar（文件名中的 `1.21.11` / `26.2` / `26.3` 即目标版本）。
2. 把 jar 放进服务器的 `plugins/` 目录。
3. 启动一次服务器，让插件生成 `plugins/Nascraft/` 配置与数据目录。
4. 在 `plugins/Nascraft/config.yml` 中确认语言（本仓库默认即为中文）：

   ```yaml
   language: 'zh_CN'
   ```

5. 重启服务器。

> **升级提示**：不要删除 `plugins/Nascraft/` 数据目录（里面有 `sqlite.db` 与你的配置）。升级后如果语言文件缺少新键，删除一次 `plugins/Nascraft/langs/` 让插件重新生成即可。

## 命令与权限

| 命令 | 别名 | 说明 | 权限节点 |
|------|------|------|----------|
| `/market` | `/shop` | 打开市场界面 | `nascraft.market` + `nascraft.market.gui` |
| `/sellhand` | `/nsellhand` | 把手持物品直接卖给市场 | `nascraft.sellhand` |
| `/sellmenu` | `/sell` | 打开批量出售界面 | `nascraft.sellmenu` |
| `/sellall` | `/nsellall` | 出售背包中所有可卖物品 | `nascraft.sellall` |
| `/portfolio` | `/p` | 查看资产组合 | `nascraft.portfolio` |
| `/alerts` | `/nalerts` | 查看价格提醒 | `nascraft.alert` |
| `/setalert` | `/nsetalert` | 设置价格提醒 | `nascraft.alert` |
| `/discord` | `/dis` | Discord 相关选项 | `nascraft.discord` |
| `/link` | `/nlink` | 绑定 Discord 账号 | `nascraft.linkable` |
| `/linkqq` | `/qq` | 绑定 / 解绑 QQ（OneBot） | `nascraft.qq.linkable` |
| `/givesellwand` | `/givesw` | 给予出售魔杖（管理员） | `nascraft.admin` |
| `/nascraft` | `/nas` | 管理员命令（编辑市场 / 重载等） | `nascraft.admin` |

- 别名可在 `config.yml` 的 `commands:` 段修改或关闭对应命令。
- `/market` 的子命令：`/market category <分类>`、`/market item <物品ID>`、`/market buy|sell <物品ID> <数量>`（控制台可用带玩家参数的形式）。
- `/nascraft` 的子命令：`edit`（市场编辑器）、`reload`、`save`、`logs`、`info`、`stop` / `resume`（暂停 / 恢复交易）、`forgivedebt`、`migrate mysql`。
- 当 `config.yml` 中 `market-permission: true` 时，打开市场会额外校验 `nascraft.market`；权限节点可在权限插件中自由分配。

## QQ 机器人（OneBot v11）

插件内置 OneBot v11 桥接，同一套配置即可接入个人 QQ 或 QQ 官方机器人渠道：

| 渠道 | 推荐实现 | 说明 |
|------|----------|------|
| 个人 QQ | NapCat / LLOneBot / Lagrange / go-cqhttp | 以 OneBot v11 正向 HTTP API + 反向 HTTP 上报接入 |
| QQ 官方机器人 | gensokyo / `@onebots/adapter-qq` | 把 QQ 开放平台机器人转换成 OneBot v11，插件侧配置完全相同 |

启用步骤：

1. 启动你的 OneBot v11 实现，记下 HTTP API 地址（默认 `http://127.0.0.1:3000`）和 access token。
2. 在 `config.yml` 中开启并填写：

   ```yaml
   onebot:
     enabled: true
     api-url: 'http://127.0.0.1:3000'
     access-token: ''
     webhook:
       enabled: true
       host: 127.0.0.1
       port: 8765
       path: '/onebot'
       secret: ''
   ```

3. 在 OneBot 实现里把「反向 HTTP 上报」指向 `http://<host>:<port>/onebot`（对应上面的 host / port / path），并设置相同的 secret。
4. 重启服务器；玩家在游戏内执行 `/linkqq` 获取 6 位绑定码，再在 QQ 私聊或群里发送 `绑定 <绑定码>`。

QQ 端命令：

| 命令 | 说明 |
|------|------|
| `帮助` | 查看可用命令 |
| `行情 <物品>` | 查询价格、库存与日涨跌 |
| `提醒 <物品> <价格>` | 设置价格提醒（低于当前价记为跌到提醒，高于当前价记为涨到提醒） |
| `提醒列表` / `取消提醒 <物品>` | 查看或删除自己的提醒 |
| `绑定 <绑定码>` / `解绑` | 绑定或解绑游戏账号 |

> 群聊默认允许所有群；可在 `onebot.commands.group-whitelist` 中填写群号限制。交易日志需开启 `onebot.log-trades.enabled` 并填写 `onebot.log-trades.group-ids`。游戏内可用 `/linkqq unlink` 解绑。

## 配置

主要文件都在 `plugins/Nascraft/` 下：

| 文件 | 用途 |
|------|------|
| `config.yml` | 数据库、跨服、Discord、网页、借贷、限价单、命令开关等全部主配置 |
| `categories.yml` | 市场分类 |
| `items.yml` | 可交易物品与初始价格 |
| `inventorygui.yml` | 界面布局与文本 |
| `investments.yml` | 资产 / 借贷相关参数 |
| `langs/zh_CN.yml` | 中文语言文件 |

完整注释见 `config.yml`（本仓库已汉化）。

## 从源码构建

### 前置

- **JDK 21 与 JDK 25**（工具链路径通过 `gradle.properties` 的 `org.gradle.java.installations.paths` 指定）
- **Gradle 8.14.3**（仓库自带 wrapper）
- 能访问 Maven 仓库与 GitHub；下载依赖困难时可配置代理

> `gradle.properties` 中的代理与 `D:/Zulu`，本地构建前请改成你自己的路径

### 构建命令

```bash
./gradlew shadowJar "-PmcTarget=1.21.11"   # 1.21.11（默认）
./gradlew shadowJar "-PmcTarget=26.2"      # 26.2
./gradlew shadowJar "-PmcTarget=26.3"      # 26.3
```

> **PowerShell 用户注意**：`-PmcTarget=1.21.11` 必须整体加引号。不加引号时 PowerShell 会把参数拆坏，Gradle 只收到 `.21.11`，直接报 `Task '.21.11' not found` 并构建失败；此时 `build/libs` 里留着的是上一次构建的旧 jar，发出去会得到「没打依赖、启动报 `NoClassDefFoundError`」的坏包。CMD 或 Linux / macOS 的 shell 不需要加引号。

构建产物（版本号取自 `gradle.properties`）：

```
build/libs/Nascraft-<version>-1.21.11.jar
build/libs/Nascraft-<version>-26.2.jar
build/libs/Nascraft-<version>-26.3.jar
```

### 测试

```bash
./gradlew test "-PmcTarget=26.3"
```

`src/test` 下的 `ConnectionReuseTest` 是 SQLite 连接复用的回归测试：历史上「查库过程中又查一次库」会把自己锁死，并连带卡住服务器主线程 30 秒（打开资产榜单时的 `SQLTransientConnectionException`），这些测试用来防止它再次出现。

### 构建细节

- **Gradle daemon 必须运行在 JDK 21 上**（即使构建 26.2 / 26.3），因为 Gradle 8.14.3 内嵌的 Kotlin DSL 编译器无法解析 Java 25 的版本号；`javac` 会通过 Java 工具链自动切换到目标 JDK。
- `shadowJar` 结束后会自动做一次**打包自检**：确认 bStats、invui、item-nbt-api 等随包依赖确实被 shade 进 jar，缺任何一个都会让构建失败，避免再发出缺依赖的包。

## 自动构建与发布

| 工作流 | 触发方式 | 作用 |
|--------|----------|------|
| `.github/workflows/build.yml` | push、Pull Request、手动 | 跑测试 + 构建三个目标并上传产物（Actions 页面的 Artifacts 可下载） |
| `.github/workflows/release.yml` | 推 `v*` tag，或手动触发 | 递增 / 指定版本号 → 提交 → 打 tag → 跑测试 → 构建 → 发布 Release |

手动发布：Actions → 发布 Release → Run workflow，填版本号（例如 `26.4.2`）或选择递增方式即可。

> 工作流会在构建前移除 `gradle.properties` 中作者本机的代理与工具链行，再写入 runner 上真实的 JDK 路径；如果 GitHub Release 资源返回 504，会自动回退到腾讯云 / 华为云镜像下载 Gradle，并校验 SHA-256 后继续构建。

## 本仓库相对原版的改动

- 适配 1.21.11、26.2 与 26.3（invui 1.x / 2.x）；26.3 使用 invui 2.5.1 修复地图 / 制图台窗口的 `NoSuchMethodError`。
- 版本号自 26.3 起与 MC 版本对齐，统一由 `gradle.properties` 的 `version` 控制。
- 修复随包依赖没有被打进 jar 的问题（bStats / invui / item-nbt-api 缺失时，服务器加载会报 `NoClassDefFoundError: org/bstats/charts/CustomChart`）；`shadowJar` 现在带打包自检。
- anvil 输入弹窗不再依赖 anvilgui（其 NMS 包装只支持到 1.21.10，在 1.21.11 上会报 `NoClassDefFoundError`），改用 invui `AnvilWindow`。
- 物品贴图未就绪时使用占位图标，物品始终可以加载；客户端 JAR 下载完成后重载即可显示真实贴图。
- 修复 `/market` 在市场无物品时的空列表崩溃。
- 替换已弃用的 `JavaPlugin.getPluginLoader()` 调用，为后续 Paper API 移除做准备。
- 新增 OneBot v11 桥接：QQ 端交易日志、行情查询、价格提醒与账号绑定，兼容个人 QQ 与 QQ 官方机器人渠道。
- 完整中文（zh_CN）语言包，配置注释与默认显示名称均已汉化。
- CI 增加 Gradle 下载镜像回退（校验 SHA-256），GitHub 发布资源不可用时也能完成构建。

## 常见问题

<details>
<summary><b>26.4 什么时候支持？</b></summary>

Paper 目前还没有发布 26.4 的 `paper-api` 与 26.4 构建，因此暂时无法编译真正的 26.4 目标。等 Paper 发布 26.4 API 后会跟进加入构建矩阵。
</details>

<details>
<summary><b>打开 /market 的图表 / 制图台窗口报 NoSuchMethodError: asCraftMirror？</b></summary>

这是 26.3 的 CraftBukkit 移除了 `CraftItemStack.asCraftMirror`、而 invui 2.3.0 仍在调用旧方法导致的。v26.4.1 起 26.3 目标已改用 invui 2.5.1 修复，升级到最新 Release 即可。
</details>

<details>
<summary><b>启动报 NoClassDefFoundError: org/bstats/charts/CustomChart？</b></summary>

说明用的是没有 shade 依赖的旧包或自行构建失败的包。请使用 Releases 中带依赖的 jar；自行构建时确认 `shadowJar` 自检通过。
</details>

<details>
<summary><b>PowerShell 构建报 Task '.21.11' not found？</b></summary>

`-PmcTarget=1.21.11` 需要整体加引号，见 [构建命令](#构建命令)。
</details>

<details>
<summary><b>中文语言文件不生效或缺少新文本？</b></summary>

删除 `plugins/Nascraft/langs/` 下的旧文件，重启让插件按当前版本重新生成。
</details>

<details>
<summary><b>怎么接入 QQ 机器人 / QQ 官方机器人？</b></summary>

见 [QQ 机器人（OneBot v11）](#qq-机器人onebot-v11)。个人 QQ 用 NapCat / LLOneBot / Lagrange / go-cqhttp；QQ 官方机器人用 gensokyo 或 `@onebots/adapter-qq` 转成 OneBot v11，插件侧配置完全相同。
</details>

<details>
<summary><b>依赖下载失败或 GitHub 资源 504？</b></summary>

CI 已内置镜像回退；本地构建可以配置代理，或手动下载 Gradle 发行版使用。详见 [自动构建与发布](#自动构建与发布)。
</details>

## 致谢

- 原作者 **Bounser** 与 [Nascraft](https://www.spigotmc.org/resources/108216/) 项目。
- 所有提交 issue、反馈日志与帮助测试的玩家。
- 本仓库负责 Paper 新版本适配、汉化维护与构建发布。

## 许可证

本项目基于 **MIT License** 发布，原始版权归 Bounser 所有。详见 [LICENSE](LICENSE)。

<div align="center">
<sub>Nascraft26P · 为 Paper 生态持续维护 · 用价格讲故事</sub>
</div>
