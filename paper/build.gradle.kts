import org.gradle.api.tasks.SourceSetContainer

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.22"
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
    paperweight.paperDevBundle("26.2.build.+")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.5.27")
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val pluginVersion = rootProject.providers.gradleProperty("pluginVersion").orElse("1.0.0")
val generatedVersionDir = layout.buildDirectory.dir("generated/sources/version/main")
val generateVersionSource by tasks.registering {
    inputs.property("pluginVersion", pluginVersion)
    outputs.dir(generatedVersionDir)
    doLast {
        val output = generatedVersionDir.get().file(
            "net/sneakycharactermanager/paper/BuildVersion.java"
        ).asFile
        output.parentFile.mkdirs()
        output.writeText(
            "package net.sneakycharactermanager.paper;\n\n" +
                "final class BuildVersion {\n" +
                "    static final String VALUE = \"${pluginVersion.get()}\";\n" +
                "    private BuildVersion() {}\n" +
                "}\n"
        )
    }
}

extensions.getByType<SourceSetContainer>().named("main") {
    java.srcDir(generatedVersionDir)
}
tasks.compileJava { dependsOn(generateVersionSource) }
tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach { dependsOn(generateVersionSource) }

tasks {
    test {
        useJUnitPlatform()
    }
}
