# libs/invui-2.5.1-relocated.jar

预打包的 **invui 2.5.1**，专供 Nascraft 的单 jar 发布使用。

## 为什么需要预打包

Nascraft 现在只发一个 jar，要同时跑在：

| MC / Paper 版本 | Java | invui |
| --- | --- | --- |
| 1.21.11 | 21 | 1.49（原生包名 `xyz.xenondevs.invui.*`） |
| 26.1.2 / 26.2 / 26.3 | 25 | 2.5.1（本文件，重定位包名） |

两个原因必须重打包：

1. **包名冲突**：invui 1.x 与 2.x 都占 `xyz.xenondevs.invui.*`，同一 jar 里不能共存。
   本 jar 把 `xyz.xenondevs.**` 全部重定位到
   `me.bounser.libs.invui2.xyz.xenondevs.**`，两套互不干扰。
2. **26.x 内部 API 改名**：Paper 26.3 把 `CraftItemStack.asCraftMirror` 改名为
   `asBukkitMirror`，而 26.1/26.2 里只有旧名。invui 2.3.0 只会调旧名（26.3 崩），
   invui 2.5.1 只会调新名（26.1/26.2 崩）。本 jar 把
   `xyz/xenondevs/invui/internal/menu/CustomCartographyMenu.class` 里那一处调用
   换成反射兼容层：先找 `asBukkitMirror`，没有就退回 `asCraftMirror`，
   于是同一份 invui 2.5.1 能在 26.1–26.3 上工作。

## 字节码版本

本 jar 的类仍是 **major 69（Java 25）**：它只会在 Paper 26.x（强制 Java 25）上被加载，
所以没必要降到 21；Nascraft 自己的代码用 `--release 21` 编出 major 65，在 1.21.11 的
Java 21 上可以正常加载，且永远不会去碰本 jar 里的类。

## 生成方式

生成脚本与验证输出在 `F:\n26p\work\invui2\`（不随仓库发布，见对应 `.md` 报告）：

- 输入：Gradle 缓存里的 `xyz.xenondevs.invui:invui:2.5.1` 官方 jar；
- ASM 补丁：`CustomCartographyMenu` 的 `CraftItemStack.asBukkitMirror` 调用 → 反射兼容 helper；
- ASM 重定位：`xyz.xenondevs` → `me.bounser.libs.invui2.xyz.xenondevs`；
- 校验：包内无残留 `xyz/xenondevs/**` 条目；`javap` 确认不再直接引用 `CraftItemStack.as*Mirror`；
  JDK 25 下 `Class.forName` 关键类可加载。

## 升级注意

- 本文件是二进制依赖，升级 invui 版本时需要重新生成，不能只改版本号。
- 不要用 `relocate` 再处理它（包名已经是最终形态），也不要把它加进 `plugin.yml` 的 `libraries`。
