plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
}

repositories {
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
	mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.21.8-R0.1-SNAPSHOT")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("com.googlecode.json-simple:json-simple:1.1.1")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("net.luckperms:api:5.4")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks {
    assemble {
        dependsOn(reobfJar)
    }
}