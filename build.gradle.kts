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
// Build target selection.
//   gradle shadowJar -PmcTarget=1.21.11   -> MC 1.21.11 jar (invui 1.x, JDK 21)
//   gradle shadowJar -PmcTarget=26.2      -> MC 26.2 jar   (invui 2.x, JDK 25)
//   gradle shadowJar -PmcTarget=26.3      -> MC 26.3 jar   (invui 2.x, JDK 25)
//
// 【PowerShell 用户注意】参数必须整体加引号：
//     .\gradlew.bat shadowJar "-PmcTarget=1.21.11"
// 不加引号时 PowerShell 会把 1.21.11 拆坏，Gradle 只收到 ".21.11"，直接报
//     Task '.21.11' not found
// 构建失败后 build/libs 里留着的是上一次构建的旧 jar；如果这时候把旧 jar 发出去，
// 就会得到 issue #3 里那个「没打依赖、启动报 NoClassDefFoundError」的包。
// ------------------------------------------------------------------------
val mcTarget: String = (findProperty("mcTarget") as String?) ?: "1.21.11"

val paperApiVersion = when (mcTarget) {
    "1.21.11" -> "1.21.11-R0.1-SNAPSHOT"
    "26.2"    -> "26.2.build.92-stable"
    // 26.3 目前 Paper 只发到 beta；等出 stable 之后把这里换掉即可。
    "26.3"    -> "26.3.build.147-beta"
    else -> throw GradleException("未知的 mcTarget：'$mcTarget'（可选：1.21.11 / 26.2 / 26.3）")
}

val useInvui2    = mcTarget != "1.21.11"
val invuiVersion = if (useInvui2) "2.3.0" else "1.49"
val toolchainJvm = if (useInvui2) 25 else 21   // paper-api 26.2/26.3 是 Java 25 编译的

java {
    // The 26.2 target runs on Java 25 (paper-api 26.2 + invui 2.3.0 are compiled for
    // Java 25 only), so its bytecode and the JVM attribute Gradle uses to resolve the
    // runtime classpath must be 25 as well. The 1.21.11 target stays on Java 21.
    // The toolchain is set explicitly (was previously configured via the removed
    // kotlin { jvmToolchain } block) so javac runs on the matching JDK. Note the
    // Gradle daemon itself must stay on Java 21: Gradle 8.14.3's embedded Kotlin DSL
    // compiler cannot parse Java 25's version string ("25.0.3").
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(toolchainJvm))
    }
    sourceCompatibility = JavaVersion.toVersion(toolchainJvm)
    targetCompatibility = JavaVersion.toVersion(toolchainJvm)
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
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    if (useInvui2) {
        // invui 2.x ships everything in a single jar.
        implementation("xyz.xenondevs.invui:invui:$invuiVersion")
    } else {
        // invui 1.x is a POM aggregator; pull in its transitive modules
        // (inventory-access, etc.). Must stay >= 1.49 for MC 1.21.11.
        implementation("xyz.xenondevs.invui:invui:$invuiVersion@pom") { isTransitive = true }
    }

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
    implementation("org.bstats:bstats-bukkit:3.0.2")
    compileOnly("redis.clients:jedis:5.1.2")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("de.tr7zw:item-nbt-api:2.13.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly   ("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:3.43.0.0")
    // HikariCP 只是 compileOnly，测试运行时也要用（连接复用的回归测试自己建池）
    testImplementation("com.zaxxer:HikariCP:5.1.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
}

// The 26.2 / 26.3 builds use the invui 2.x GUI sources. They live in src/invui2/java
// (same packages) and are staged into build/invui2-staged-src together with
// the rest of src/main/java minus the invui 1.x GUI files.
val stageInvui2 = tasks.register<Copy>("stageInvui2Source") {
    enabled = useInvui2
    into(layout.buildDirectory.dir("invui2-staged-src"))
    from("src/main/java") {
        // Only the six files that have invui 2.x counterparts in src/invui2 are
        // replaced. Everything else in those packages (PortfolioInventory,
        // PortfolioChartType, TopMenu, DebtMenu, ...) is kept as-is.
        exclude("me/bounser/nascraft/inventorygui/MiniChart/InfoMenu.java")
        exclude("me/bounser/nascraft/inventorygui/MiniChart/StatsItem.java")
        exclude("me/bounser/nascraft/inventorygui/MiniChart/TimeFrameItem.java")
        exclude("me/bounser/nascraft/inventorygui/Portfolio/InfoPortfolio.java")
        exclude("me/bounser/nascraft/inventorygui/Portfolio/ModeItem.java")
        exclude("me/bounser/nascraft/inventorygui/Portfolio/PortfolioStatsItem.java")
        // invui 2.x anvil API differs from 1.x; src/invui2/java provides the
        // version-specific AnvilPrompt for the 26.2 target.
        exclude("me/bounser/nascraft/util/AnvilPrompt.java")
    }
    from("src/invui2/java")
}

if (useInvui2) {
    sourceSets {
        main {
            java.setSrcDirs(listOf(layout.buildDirectory.dir("invui2-staged-src")))
        }
    }
    tasks.named<JavaCompile>("compileJava") {
        dependsOn(stageInvui2)
    }
    tasks.named<JavaCompile>("compileTestJava") {
        dependsOn(stageInvui2)
    }
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
        archiveFileName.set("Nascraft-${project.version}-${mcTarget}.jar")

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
                "xyz/xenondevs/invui/gui/AbstractGui.class",  // invui 界面库
                "me/bounser/nbtapi/NBTItem.class",            // item-nbt-api（已重定位）
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
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        // 直接跑当前构建目标对应的版本，既能验对应的 jar，也不用再去查
        // 「最新 Paper 版本」的接口（旧接口 api.papermc.io/v2 已经返回 410）。
        minecraftVersion(mcTarget)
        jvmArgs("-Dcom.mojang.eula.agree=true")
        downloadPlugins {
            github("milkbowl", "Vault", "1.7.3", "Vault.jar")
            hangar("PlaceholderAPI", "2.11.6")
            github("EssentialsX", "Essentials", "2.21.2", "EssentialsX-2.21.2.jar")
        }
    }
}
