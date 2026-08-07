# Nascraft26P

Nascraft 市场的汉化改造版（Paper 服务器插件）。这是一个由玩家交易驱动的动态物品市场：价格随买卖量实时波动，支持税制、噪音、限价单、资产组合、借贷，以及 SQLite/MySQL/Redis 跨服、Discord 机器人、自托管网页。

原版说明见：https://www.spigotmc.org/resources/108216/

## 版本与构建目标

| 目标   | 适用版本       | Java | 界面库    |
|--------|----------------|------|-----------|
| 1.21.11 | Paper / Purpur 1.21.11 | JDK 21 | invui 1.x |
| 26.2   | Paper / Purpur 26.2    | JDK 25 | invui 2.x |

两个目标共享同一套源码；仅 26.2 会额外把 `src/invui2/java` 下的 6 个界面文件和 `AnvilPrompt`（invui 2.x 版）覆盖进构建。

## 功能

- 玩家驱动的动态价格，买卖税、噪声波动、支撑位/压力位、弹性
- 主菜单、分类页、热门趋势、涨跌幅榜单
- 限价单（限时）、超卖/超买限制
- 资产组合（portfolio）与借贷、利息、清算
- 子物品（child）价格按比例联动
- SQLite（单服）/ MySQL（多服共享）+ Redis 实时同步
- Discord 桥接：交易日志、链接账号、警报
- 自托管网页：行情、图表、会话登录
- 内置中文语言包（zh_CN），`/nascraft edit` 管理员编辑器已汉化

## 环境要求

- Paper / Purpur 1.21.11 或 26.2
- Vault（必装，作为默认货币）
- 可选：PlaceholderAPI（多货币）、AdvancedGUI（游戏内图表）
- 依赖：redis.clients:jedis（跨服用）、JDA（Discord 用）

## 构建

前置：

- JDK 21 和 JDK 25（工具链路径在 `gradle.properties` 的 `org.gradle.java.installations.paths` 中配置）
- Gradle 8.14.3
- 依赖下载需要代理（`gradle.properties` 已配置 127.0.0.1:7897）

命令：

```
gradle shadowJar -PmcTarget=1.21.11   # 1.21.11（默认）
gradle shadowJar -PmcTarget=26.2      # 26.2
```

注意：Gradle daemon 必须运行在 JDK 21 上（即使构建 26.2 目标），因为 Gradle 8.14.3 内嵌的 Kotlin DSL 编译器无法解析 Java 25 的版本号 `25.0.3`。javac 会自动通过 Java 工具链切换到对应 JDK。

产物：

- `build/libs/Nascraft-1.9.2-1.21.11.jar`
- `build/libs/Nascraft-1.9.2-26.2.jar`

## 安装

1. 用对应版本的 jar 替换服务器 `plugins/` 里的旧 jar。
2. 不要删除 `plugins/Nascraft` 数据文件夹，里面是数据库（sqlite.db）和你的配置。
3. 删除一次 `plugins/Nascraft/langs/` 下的旧语言文件，让插件重新生成（新增的键才能生效）。
4. 在 `plugins/Nascraft/config.yml` 中设置 `language: 'zh_CN'`。
5. 重启服务器。

## 本仓库相对原版的改动

- 适配 1.21.11 与 26.2（invui 1.x / 2.x）。
- anvil 输入弹窗不再依赖 anvilgui（其 NMS 包装只支持到 1.21.10，在 1.21.11 上会报 `NoClassDefFoundError`），改用 invui AnvilWindow。
- 物品贴图未就绪时使用占位图标，物品始终可以加载；客户端 JAR 下载完成后重载即可显示真实贴图。
- 修复 `/market` 在市场无物品时的空列表崩溃。
- 完整中文（zh_CN）语言包，配置注释与默认显示名称均已汉化。
