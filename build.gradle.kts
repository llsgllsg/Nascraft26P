import java.util.zip.ZipFile

// shadow 8.3.5 bundles ASM 9.7.1, which cannot read Java 25 class files
// (major version 69). The 26.2 target compiles to Java 25 bytecode and shades
// invui 2.3.0 (also major 69), so relocation would fail. Force a newer ASM on
// the buildscript classpath; Gradle resolves the highest version for the plugin.
buildscript {
    dependencies {
        classpath("org.ow2.asm:asm:9.9")
        classpath("org.ow2.asm:asm-commons:9.9")
        classpath("org.ow2.asm:asm-tree:9.9")
        classpath("org.ow2.asm:asm-analysis:9.9")
        classpath("org.ow2.asm:asm-util:9.9")
    }
}

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "me.bounser"

// 版本号唯一来源：gradle.properties 里的 `version`（Gradle 会自动赋给 project.version）。
// 这里只保留一个兜底值，不要再往脚本里写死版本号——否则会出现 tag 已经是新版本、
// 打出来的 jar 文件名和 plugin.yml 还是旧版本的情况。
version = (findProperty("version") as String?) ?: "26.3"

// ------------------------------------------------------------------------
// 单一构建产物：一个 jar 通吃 1.21.11 和 Paper 26.1+
//
//   gradle shadowJar          -> build/libs/Nascraft-<version>.jar
//
// 运行时由 me.bounser.nascraft.util.ServerCompat 读取 Bukkit 版本自动选 GUI 后端：
//   MC 1.21.x   （Java 21）-> invui 1.49，原生 xyz.xenondevs.invui 包（major 65）
//   Paper 26.1+ （Java 25）-> invui 2.5.1，重定位到 me.bounser.libs.invui2.xyz.xenondevs.*
//                             （major 69，只有 26.x 的 Java 25 会去加载它）
// 两套 invui 同时打进同一个 jar，包名不冲突，各自的 GUI 实现类也分开命名
// （InfoMenuInvui1 / InfoMenuInvui2 之类），由 *Invui1 / *Invui2 门面分发。
//
// 编译策略：
//   - 统一对 paper-api 1.21.11 编译：最老的 API，26.x 上这些调用点都还在；
//     同一份源码也验证过能对 26.3 的 API 编译通过，所以不必再分目标。
//   - 用 JDK 25 的 javac + --release 21：产物是 Java 21 字节码（major 65），
//     1.21.11 的 Java 21 能加载；JDK 25 的 javac 也能读 major 69 的 invui 2.5.1。
//
// -PmcTarget 现在只影响 runServer 冒烟测试跑哪个服务器版本，不影响产物：
//     .\gradlew.bat runServer "-PmcTarget=26.3"
// （PowerShell 里必须整体加引号，否则 26.3 会被拆成 .3，报 Task '.3' not found。）
// ------------------------------------------------------------------------
val mcTarget: String = (findProperty("mcTarget") as String?) ?: "1.21.11"

// runServer 冒烟测试目标。26.1.1 只有 alpha 构建，stable 只有 26.1.2。
val smokeTestServer: String = when (mcTarget) {
    "1.21.11", "26.1.2", "26.2", "26.3" -> mcTarget
    else -> throw GradleException(
        "未知的 mcTarget：'$mcTarget'（可选：1.21.11 / 26.1.2 / 26.2 / 26.3）"
    )
}

// 编译基线：最老的受支持 API。26.x 的 API 只增不改，所以这份代码也能在 26.x 上跑。
val compilePaperApiVersion = "1.21.11-R0.1-SNAPSHOT"

java {
    // Gradle 守护进程必须留在 Java 21（Gradle 8.14.3 的 Kotlin DSL 解析不了 Java 25
    // 的版本串），所以显式指定 toolchain 25 来拿 Java 25 的 javac。
    // 字节码目标固定 Java 21：同一个 jar 要能在 1.21.11 的 Java 21 里加载。
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    // --release 21 让 javac 按 Java 21 的 API/字节码版本编译（major 65），
    // 即使 toolchain 是 JDK 25、classpath 上有 major 69 的 invui 2.5.1 也不受影响。
    options.release.set(21)
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://maven.respark.dev/releases")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://m2.dv8tion.net/releases")
    maven("https://nexus.scarsz.me/content/groups/public/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.xenondevs.xyz/releases")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$compilePaperApiVersion")

    // invui 1.x：POM 聚合体，把 inventory-access 等传递模块一起拉进来，
    // 打包时留在原生 xyz.xenondevs.invui 包，供 MC 1.21.11 使用。
    implementation("xyz.xenondevs.invui:invui:1.49@pom") { isTransitive = true }

    // invui 2.x：已预打包为重定位 + 反射兼容的 jar（见 libs/README-invui2.md），
    // 类名都在 me.bounser.libs.invui2.* 下，与上面的 1.x 包完全隔离。
    // 该 jar 是 Java 25 字节码，只有在 Paper 26.x 上才会被加载。
    implementation(files("libs/invui-2.5.1-relocated.jar"))

    compileOnly("jfree:jfreechart:1.0.13")

    compileOnly("me.leoko.advancedgui:AdvancedGUI:2.2.8")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("org.xerial:sqlite-jdbc:3.43.0.0")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { exclude(group = "org.bukkit", module = "bukkit") }
    compileOnly("com.discordsrv:discordsrv:1.28.0")
    compileOnly("commons-io:commons-io:2.14.0")

    compileOnly("net.dv8tion:JDA:5.0.0-beta.18")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("org.bstats:bstats-bukkit:3.2.1")
    compileOnly("redis.clients:jedis:5.1.2")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("de.tr7zw:item-nbt-api:2.13.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly   ("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:3.43.0.0")
    // HikariCP 只是 compileOnly，测试运行时也要用（连接复用的回归测试自己建池）
    testImplementation("com.zaxxer:HikariCP:5.1.0")
    // 用来在「不调用构造函数」的前提下造一个 DatabaseManager 实例。
    // 不用 Mockito 的静态 mock：那需要动态挂 java agent，在 CI 上会失败。
    testImplementation("org.objenesis:objenesis:3.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("io.papermc.paper:paper-api:$compilePaperApiVersion")
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("Nascraft")
        // 单产物：不再带 -1.21.11 / -26.2 / -26.3 后缀，运行时自己判断后端。
        archiveFileName.set("Nascraft-${project.version}.jar")

        dependencies {
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
            exclude(dependency("net.dv8tion:JDA:.*"))
            exclude(dependency("jfree:jfreechart:.*"))
            exclude(dependency("com.zaxxer:HikariCP:.*"))
            exclude(dependency("redis.clients:jedis:.*"))
            exclude(dependency("org.jetbrains.kotlin:.*:.*"))
        }

        relocate("org.bstats", "me.bounser.bstats")
        relocate("de.tr7zw.changeme.nbtapi", "me.bounser.nbtapi")

        // ------------------------------------------------------------------
        // 打包自检（防 issue #3 回归）
        //
        // issue #3 的原因不是代码，而是发布出去的 jar 里根本就没有随包依赖：
        // 那种 jar 只有插件自己的类，体积看着也不算离谱，但装到服务器上会直接
        //     NoClassDefFoundError: org/bstats/charts/CustomChart
        // 于是整个插件加载失败。光看文件大小是分辨不出来的，所以这里逐个确认
        // 重定位后的依赖真的进了包；少了任何一个就让 shadowJar 失败，
        // 从根上拦住「把没打依赖的包发出去」。
        // ------------------------------------------------------------------
        doLast {
            val jar = archiveFile.get().asFile
            val required = listOf(
                "me/bounser/bstats/charts/CustomChart.class", // bStats（已重定位）
                "me/bounser/bstats/bukkit/Metrics.class",
                "xyz/xenondevs/invui/gui/AbstractGui.class",  // invui 1.x（1.21.11 用）
                "me/bounser/nbtapi/NBTItem.class",            // item-nbt-api（已重定位）
                // invui 2.x（26.x 用）必须真的进包，否则 26.x 上会 NoClassDefFoundError
                "me/bounser/libs/invui2/xyz/xenondevs/invui/gui/AbstractGui.class",
                "me/bounser/libs/invui2/xyz/xenondevs/invui/window/CartographyWindow.class",
            )
            // 注意：这里必须用 import 进来的 ZipFile。
            // 在 Kotlin DSL 里写全限定名 java.util.zip.ZipFile 会被解析成
            // 名为 java 的扩展（JavaPluginExtension），报 "Unresolved reference: util"。
            val present = ZipFile(jar).use { zip ->
                zip.entries().asSequence().map { it.name }.toHashSet()
            }
            val missing = required.filterNot { it in present }
            if (missing.isNotEmpty()) {
                throw GradleException(
                    "打包自检未通过：${jar.name} 里缺少随包依赖 -> ${missing.joinToString()}。" +
                        "这样的 jar 装到服务器会报 NoClassDefFoundError，已中止构建。"
                )
            }
            logger.lifecycle(
                "打包自检通过：${jar.name}（含 bStats / invui / nbtapi，共 ${present.size} 个条目）"
            )
        }
    }

    test {
        useJUnitPlatform()
        // Mockito 5.14 的 Byte Buddy 尚未支持 Java 25（会抛 "Java 25 (69) is not supported"），
        // 测试 JVM 固定用 JDK 21 —— 插件字节码本身就是 Java 21 目标，这样也顺带验证了
        // 「产出的 class 能被 Java 21 加载」。
        javaLauncher.set(
            project.extensions.getByType(org.gradle.jvm.toolchain.JavaToolchainService::class.java).launcherFor {
                languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
            }
        )
        // 测试失败时把完整堆栈打进日志，否则只有一行摘要没法排查
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        // 冒烟测试：同一个 jar 分别丢到各版本的 Paper 上跑，验证运行时后端选择。
        // 26.1.1 只有 alpha 构建，stable 从 26.1.2 开始，所以这里不带 26.1.1。
        minecraftVersion(smokeTestServer)
        jvmArgs("-Dcom.mojang.eula.agree=true")
        downloadPlugins {
            github("milkbowl", "Vault", "1.7.3", "Vault.jar")
            hangar("PlaceholderAPI", "2.11.6")
            github("EssentialsX", "Essentials", "2.21.2", "EssentialsX-2.21.2.jar")
        }
    }
}
