plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.8"
}

repositories {
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
	mavenCentral()
    maven {
        url = uri("https://maven.maxhenkel.de/repository/public")
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.5.27")
}

tasks {
}