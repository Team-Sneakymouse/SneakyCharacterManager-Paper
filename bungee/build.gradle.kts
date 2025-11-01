plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // SpigotMC snapshots
    maven("https://libraries.minecraft.net/") // Mojang repository for brigadier
}

dependencies {
    compileOnly("net.md-5:bungeecord-api:1.21-R0.4") {
        exclude(group = "com.mojang", module = "brigadier") // Exclude the problematic brigadier dependency
    }
    compileOnly("org.checkerframework:checker-qual:3.42.0") // For @NonNull annotations
}