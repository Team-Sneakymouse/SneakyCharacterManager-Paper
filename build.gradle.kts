import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    `maven-publish`
}

group = "io.github.team-sneakymouse"

version = providers.exec {
    workingDir(rootDir)
    commandLine("git", "show", "-s", "--format=%ct:%h", "--abbrev=12", "HEAD")
}.standardOutput.asText.map { commit ->
    val (timestamp, hash) = commit.trim().split(":", limit = 2)
    val date = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC)
        .format(Instant.ofEpochSecond(timestamp.toLong()))
    "$date-$hash"
}.get()

val pomName = providers.gradleProperty("POM_NAME").orElse("SneakyCharacterManager")
val pomDescription = providers.gradleProperty("POM_DESCRIPTION")
    .orElse("Paper/Bungee plugin for switching characters")
val pomUrl = providers.gradleProperty("POM_URL")
    .orElse("https://github.com/Team-Sneakymouse/SneakyCharacterManager-Paper")
val pomScmUrl = providers.gradleProperty("POM_SCM_URL").orElse(pomUrl)
val pomScmConnection = providers.gradleProperty("POM_SCM_CONNECTION")
    .orElse("scm:git:git://github.com/Team-Sneakymouse/SneakyCharacterManager-Paper.git")
val pomScmDeveloperConnection = providers.gradleProperty("POM_SCM_DEV_CONNECTION")
    .orElse("scm:git:ssh://git@github.com:Team-Sneakymouse/SneakyCharacterManager-Paper.git")
val pomLicenseName = providers.gradleProperty("POM_LICENSE_NAME")
    .orElse("GNU General Public License v3.0")
val pomLicenseUrl = providers.gradleProperty("POM_LICENSE_URL")
    .orElse("https://www.gnu.org/licenses/gpl-3.0-standalone.html")
val pomDeveloperId = providers.gradleProperty("POM_DEVELOPER_ID")
    .orElse("team-sneakymouse")
val pomDeveloperName = providers.gradleProperty("POM_DEVELOPER_NAME")
    .orElse("Team Sneakymouse")

fun org.gradle.api.publish.maven.MavenPom.configureSharedPom(moduleLabel: String) {
    name.set("${pomName.get()} $moduleLabel")
    description.set(pomDescription)
    url.set(pomUrl)

    licenses {
        license {
            name.set(pomLicenseName)
            url.set(pomLicenseUrl)
        }
    }

    developers {
        developer {
            id.set(pomDeveloperId)
            name.set(pomDeveloperName)
        }
    }

    scm {
        url.set(pomScmUrl)
        connection.set(pomScmConnection)
        developerConnection.set(pomScmDeveloperConnection)
    }
}

fun org.gradle.api.artifacts.dsl.RepositoryHandler.sneakyrpReleases() {
    maven {
        name = "sneakyrp"
        url = uri("https://maven.sneakyrp.com/releases")
        credentials(PasswordCredentials::class)
        authentication {
            create<org.gradle.authentication.http.BasicAuthentication>("basic")
        }
    }
}

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.maxhenkel.de/repository/public")
    }
}

dependencies {
    implementation(project(":bungee")) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(project(path = ":paper")) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(project(":proxy-common")) {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(project(":velocity")) {
        exclude(group = "org.jetbrains.kotlin")
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    extensions.getByType<SourceSetContainer>().named("main") {
        resources.srcDir("src/resources")
    }

    repositories {
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://jitpack.io")
        maven("https://maven.maxhenkel.de/repository/public")
    }

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Javadoc>().configureEach {
        isFailOnError = false
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<ProcessResources>().configureEach {
        inputs.property("version", project.version.toString())
        filesMatching(listOf("paper-plugin.yml", "bungee.yml", "velocity-plugin.json")) {
            expand("version" to project.version.toString())
        }
    }

    val moduleDisplayName = project.name.split("-").joinToString("") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }

    tasks.named<Jar>("jar") {
        archiveBaseName.set("SneakyCharacterManager-$moduleDisplayName")
    }

    afterEvaluate {
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    groupId = project.group.toString()
                    artifactId = "sneakycharactermanager-${project.name}"
                    version = project.version.toString()

                    pom {
                        configureSharedPom(project.name)
                    }
                }
            }

            repositories {
                sneakyrpReleases()
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifactId = "sneakycharactermanager"
            artifact(tasks.named("shadowJar")) {
                classifier = null
            }

            pom {
                configureSharedPom("combined")
                description.set(
                    "Combined Paper/Bungee/Velocity plugin JAR for SneakyCharacterManager"
                )
            }
        }
    }
    repositories {
        sneakyrpReleases()
    }
}

tasks {
    jar {
        enabled = false
    }
    shadowJar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes["Main-Class"] = "your.main.class"
        }

        from(subprojects.map { it.extensions.getByType<SourceSetContainer>()["main"].output })

        exclude("**/kotlin/**")
        exclude("META-INF/*.kotlin_module")

        filesMatching(listOf("paper-plugin.yml", "bungee.yml", "velocity-plugin.json")) {
            expand("version" to project.version.toString())
        }

        archiveClassifier.set("")
        archiveFileName.set("SneakyCharacterManager-${project.version}.jar")
    }
    compileJava {
        options.release = 25
    }
    build {
        dependsOn(shadowJar)
    }
    runServer {
        dependsOn(shadowJar)
        minecraftVersion("26.2")
    }

    register<Exec>("runBungee") {
        group = "SneakyCharacterManager"
        description = "Builds the jar, copies it to the Bungee server plugins folder, and runs BungeeCord."
        dependsOn(shadowJar)

        doFirst {
            val src = shadowJar.get().archiveFile.get().asFile
            val dest = file("/mnt/files/Desktop/Minecraft/bungee/plugins/SneakyCharacterManager.jar")
            if (src.exists()) {
                dest.parentFile.mkdirs()
                src.copyTo(dest, overwrite = true)
            }
        }

        workingDir = file("/mnt/files/Desktop/Minecraft/bungee")
        commandLine = listOf("java", "-jar", "BungeeCord.jar")
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(tasks.named("check"))
}

subprojects {
    tasks.withType<PublishToMavenRepository>().configureEach {
        dependsOn(tasks.named("check"))
    }
}
