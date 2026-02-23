plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "8.1.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

val pomName = providers.gradleProperty("POM_NAME").orElse("SneakyCharacterManager")
val pomDescription = providers.gradleProperty("POM_DESCRIPTION")
    .orElse("Paper/Bungee plugin for switching characters")
val pomUrl = providers.gradleProperty("POM_URL")
    .orElse("https://github.com/REPLACE_ME/SneakyCharacterManager-Paper")
val pomScmUrl = providers.gradleProperty("POM_SCM_URL").orElse(pomUrl)
val pomScmConnection = providers.gradleProperty("POM_SCM_CONNECTION")
    .orElse("scm:git:git://github.com/REPLACE_ME/SneakyCharacterManager-Paper.git")
val pomScmDeveloperConnection = providers.gradleProperty("POM_SCM_DEV_CONNECTION")
    .orElse("scm:git:ssh://git@github.com:REPLACE_ME/SneakyCharacterManager-Paper.git")
val pomLicenseName = providers.gradleProperty("POM_LICENSE_NAME")
    .orElse("The MIT License")
val pomLicenseUrl = providers.gradleProperty("POM_LICENSE_URL")
    .orElse("https://opensource.org/licenses/MIT")
val pomDeveloperId = providers.gradleProperty("POM_DEVELOPER_ID")
    .orElse("team-sneakymouse")
val pomDeveloperName = providers.gradleProperty("POM_DEVELOPER_NAME")
    .orElse("Team Sneakymouse")

val sonatypeUsername = providers.gradleProperty("sonatypeUsername")
    .orElse(System.getenv("SONATYPE_USERNAME") ?: "")
val sonatypePassword = providers.gradleProperty("sonatypePassword")
    .orElse(System.getenv("SONATYPE_PASSWORD") ?: "")
val signingKey = providers.gradleProperty("signingKey")
    .orElse(System.getenv("SIGNING_KEY") ?: "")
val signingPassword = providers.gradleProperty("signingPassword")
    .orElse(System.getenv("SIGNING_PASSWORD") ?: "")

allprojects {
    group = "io.github.team-sneakymouse"
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
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

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
        withSourcesJar()
        withJavadocJar()
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
                        name.set("${pomName.get()} ${project.name}")
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
                }
            }

            repositories {
                maven {
                    name = "sonatype"
                    // Snapshot and release repositories for Maven Central (OSSRH flow).
                    val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                    url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                    credentials {
                        username = sonatypeUsername.get()
                        password = sonatypePassword.get()
                    }
                }
            }
        }

        extensions.configure<SigningExtension> {
            val key = signingKey.orNull
            val password = signingPassword.orNull
            if (!key.isNullOrBlank() && !password.isNullOrBlank()) {
                useInMemoryPgpKeys(key, password)
                sign(extensions.getByType(PublishingExtension::class).publications)
            }
        }
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
    build {
        dependsOn(subprojects.map { it.tasks.named("publishToMavenLocal") })
    }
    register("releaseToCentral") {
        description = "Publishes all subprojects to Sonatype for Maven Central"
        group = "publishing"
        dependsOn(subprojects.map { it.tasks.named("publishMavenPublicationToSonatypeRepository") })
    }
    runServer {
        dependsOn(shadowJar)
        minecraftVersion("1.21.4")
    }
}

artifacts {
    add("archives", tasks.named("shadowJar"))
}