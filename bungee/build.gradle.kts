plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // SpigotMC snapshots
}

dependencies {
    compileOnly("net.md-5:bungeecord-api:1.20-R0.1") {
        exclude(group = "net.md-5", module = "brigadier") // Exclude the problematic brigadier dependency
    }
}