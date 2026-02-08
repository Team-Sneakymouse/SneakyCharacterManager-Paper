plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "8.1.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

allprojects {
    group = "net.sneakycharactermanager"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://maven.maxhenkel.de/repository/public")
    }
}

dependencies {
    implementation(project(":bungee")){
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(project(path=":paper")){
        exclude(group = "org.jetbrains.kotlin")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://jitpack.io")
        maven("https://maven.maxhenkel.de/repository/public")
    }

    dependencies {
        
    }

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    "shadowJar"(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        manifest {
            attributes["Main-Class"] = "your.main.class"
        }

        // Include the class files from the subprojects
        //from(subprojects.map { it.sourceSets["main"].output })

        // Include the resources from the subprojects
        from(subprojects.map { it.file("src/resources").absolutePath })

        exclude("**/kotlin/**")
        exclude("META-INF/*.kotlin_module")

        archiveBaseName.set("SneakyCharacterManager")
        archiveClassifier.set("")
    }
    compileJava {
        options.release = 21
    }
    runServer {
        dependsOn(shadowJar)
        minecraftVersion("1.21.4")
    }
}

artifacts {
    add("archives", tasks.named("shadowJar"))
}