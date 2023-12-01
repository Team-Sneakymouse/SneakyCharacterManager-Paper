plugins {
    kotlin("jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

allprojects {
    group = "net.sneakycharactermanager"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "kotlin")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
}

tasks {

    "shadowJar"(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
        manifest {
            attributes["Main-Class"] = "your.main.class"
        }

        // Include the class files from the subprojects
        from(subprojects.map { it.sourceSets["main"].output })

        // Include the resources from the subprojects
        from(subprojects.map { it.file("src/resources").absolutePath })

        configurations = listOf()

        archiveBaseName.set("SneakyCharacterManager")
        archiveClassifier.set("")
    }
}

artifacts {
    add("archives", tasks.named("shadowJar"))
}