import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val pluginVersion = rootProject.providers.gradleProperty("pluginVersion").orElse("1.0.0")
val generatedVersionDir = layout.buildDirectory.dir("generated/sources/version/main")
val generateVersionSource by tasks.registering {
    inputs.property("pluginVersion", pluginVersion)
    outputs.dir(generatedVersionDir)
    doLast {
        val output = generatedVersionDir.get().file(
            "net/sneakycharactermanager/velocity/BuildVersion.java"
        ).asFile
        output.parentFile.mkdirs()
        output.writeText(
            "package net.sneakycharactermanager.velocity;\n\n" +
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

dependencies {
    compileOnly(project(":proxy-common"))

    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")

    implementation(project(":proxy-common"))
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

