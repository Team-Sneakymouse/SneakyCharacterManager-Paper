plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("net.md-5:bungeecord-api:1.20-R0.1")
    compileOnly("io.papermc.paper:paper-api:1.20.2-R0.1-SNAPSHOT")
}