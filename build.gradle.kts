plugins {
    java
    kotlin("jvm") version "2.1.21"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "me.bounser"
version = "1.9.2"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
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
    compileOnly("org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT")

    implementation("xyz.xenondevs.invui:invui:1.43@pom") { isTransitive = true }

    implementation("jfree:jfreechart:1.0.13")

    compileOnly("me.leoko.advancedgui:AdvancedGUI:2.2.8")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("org.xerial:sqlite-jdbc:3.43.0.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("com.discordsrv:discordsrv:1.28.0")
    compileOnly("commons-io:commons-io:2.14.0")

    implementation("net.dv8tion:JDA:5.0.0-beta.18")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.3")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("net.wesjd:anvilgui:1.10.4-SNAPSHOT")
    implementation("redis.clients:jedis:5.1.2")
    implementation("de.tr7zw:item-nbt-api:2.13.1")
    implementation("io.javalin:javalin:6.6.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
    implementation("org.mindrot:jbcrypt:0.4")
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

        relocate("org.bstats", "me.bounser.bstats")
        relocate("net.wesjd.anvilgui", "me.bounser.anvilgui")
        relocate("de.tr7zw.changeme.nbtapi", "me.bounser.nbtapi")
        relocate("io.javalin", "me.bounser.web.libs.javalin")
        relocate("kotlin", "me.bounser.web.libs.kotlin")
    }

    build {
        dependsOn(shadowJar)
    }
}
