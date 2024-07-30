plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    id("io.papermc.paperweight.userdev") version "1.7.1"
}

repositories {
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
	mavenCentral()
}

dependencies {
    //compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("net.luckperms:api:5.4")
}

tasks {
}