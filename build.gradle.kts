import java.net.URI

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
    kotlin("jvm") version "2.1.21"
    id("com.gradleup.shadow") version "8.3.5"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "me.bounser"
version = "1.9.2"

// ------------------------------------------------------------------------
// Build target selection.
//   gradle shadowJar -PmcTarget=1.21.11   -> MC 1.21.11 jar (invui 1.x)
//   gradle shadowJar -PmcTarget=26.2      -> MC 26.2 jar   (invui 2.x)
// ------------------------------------------------------------------------
val mcTarget: String = (findProperty("mcTarget") as String?) ?: "1.21.11"
val useInvui2 = mcTarget == "26.2"

val paperApiVersion = if (useInvui2) "26.2.build.92-stable" else "1.21.11-R0.1-SNAPSHOT"
val invuiVersion   = if (useInvui2) "2.3.0" else "1.49"
val toolchainJvm   = if (useInvui2) 25 else 21   // paper-api 26.2 is compiled for Java 25

java {
    // The 26.2 target runs on Java 25 (paper-api 26.2 + invui 2.3.0 are compiled for
    // Java 25 only), so its bytecode and the JVM attribute Gradle uses to resolve the
    // runtime classpath must be 25 as well. The 1.21.11 target stays on Java 21.
    sourceCompatibility = JavaVersion.toVersion(toolchainJvm)
    targetCompatibility = JavaVersion.toVersion(toolchainJvm)
}

kotlin {
    jvmToolchain(toolchainJvm)
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
    implementation("net.wesjd:anvilgui:1.10.4-SNAPSHOT")
    compileOnly("redis.clients:jedis:5.1.2")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("de.tr7zw:item-nbt-api:2.13.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly   ("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:3.43.0.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
}

fun latestPaperMinecraftVersion(): String =
    URI("https://api.papermc.io/v2/projects/paper")
        .toURL()
        .readText()
        .substringAfter("\"versions\":[")
        .substringBefore("]")
        .split(",")
        .last()
        .trim('"')

// The 26.2 build uses the invui 2.x GUI sources. They live in src/invui2/java
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
        relocate("net.wesjd.anvilgui", "me.bounser.anvilgui")
        relocate("de.tr7zw.changeme.nbtapi", "me.bounser.nbtapi")
    }

    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(latestPaperMinecraftVersion())
        jvmArgs("-Dcom.mojang.eula.agree=true")
        downloadPlugins {
            github("milkbowl", "Vault", "1.7.3", "Vault.jar")
            hangar("PlaceholderAPI", "2.11.6")
            github("EssentialsX", "Essentials", "2.21.2", "EssentialsX-2.21.2.jar")
        }
    }
}
