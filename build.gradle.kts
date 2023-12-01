plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

allprojects {
    group = "net.sneakycharactermanager"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

dependencies {
    implementation(project(":bungee")){
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(project(path=":paper", configuration="reobf")){
        exclude(group = "org.jetbrains.kotlin")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }

    dependencies {
        compileOnly("net.md-5:bungeecord-api:1.20-R0.1")
        compileOnly("io.papermc.paper:paper-api:1.20.2-R0.1-SNAPSHOT")
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
}

artifacts {
    add("archives", tasks.named("shadowJar"))
}