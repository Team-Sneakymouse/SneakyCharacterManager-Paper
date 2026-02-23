import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import java.util.Properties
import org.gradle.external.javadoc.StandardJavadocDocletOptions

val localGradleProperties = Properties().apply {
    val localPropsFile = rootDir.resolve(".gradle/gradle.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

fun localGradleProperty(name: String): String = localGradleProperties.getProperty(name)?.trim().orEmpty()

fun resolveConfigValue(propertyName: String, envName: String? = null, defaultValue: String = ""): String {
    val gradleValue = providers.gradleProperty(propertyName).orNull?.trim().orEmpty()
    if (gradleValue.isNotEmpty()) return gradleValue

    if (envName != null) {
        val envValue = System.getenv(envName)?.trim().orEmpty()
        if (envValue.isNotEmpty()) return envValue
    }

    val localValue = localGradleProperty(propertyName)
    if (localValue.isNotEmpty()) return localValue

    return defaultValue
}

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
    .orElse("GNU General Public License v3.0")
val pomLicenseUrl = providers.gradleProperty("POM_LICENSE_URL")
    .orElse("https://www.gnu.org/licenses/gpl-3.0-standalone.html")
val pomDeveloperId = providers.gradleProperty("POM_DEVELOPER_ID")
    .orElse("team-sneakymouse")
val pomDeveloperName = providers.gradleProperty("POM_DEVELOPER_NAME")
    .orElse("Team Sneakymouse")

val sonatypeUsername = resolveConfigValue("sonatypeUsername", "SONATYPE_USERNAME")
val sonatypePassword = resolveConfigValue("sonatypePassword", "SONATYPE_PASSWORD")
val centralPortalNamespace = resolveConfigValue("centralPortalNamespace", defaultValue = "io.github.team-sneakymouse")
val signingKey = resolveConfigValue("signingKey", "SIGNING_KEY")
val signingPassword = resolveConfigValue("signingPassword", "SIGNING_PASSWORD")
val releaseVersion = resolveConfigValue("releaseVersion", defaultValue = "1.0-SNAPSHOT")

allprojects {
    group = "io.github.team-sneakymouse"
    version = releaseVersion

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

    tasks.withType<Javadoc>().configureEach {
        // Keep generating javadocs for Central while avoiding hard failure on legacy/missing tags.
        isFailOnError = false
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
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
                    // Central Portal compatibility endpoints for Maven-like publishing.
                    val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                    val snapshotsRepoUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
                    url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                    credentials {
                        username = sonatypeUsername
                        password = sonatypePassword
                    }
                }
            }
        }

        extensions.configure<SigningExtension> {
            val key = signingKey
            val password = signingPassword
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
    register("validateCentralConfig") {
        description = "Validates required Central publishing credentials and signing setup"
        group = "publishing"
        doLast {
            val username = sonatypeUsername.trim()
            val password = sonatypePassword.trim()
            if (username.isBlank() || password.isBlank()) {
                throw GradleException(
                    "Missing Central credentials. Set sonatypeUsername/sonatypePassword " +
                        "(or SONATYPE_USERNAME/SONATYPE_PASSWORD)."
                )
            }
            if (username.contains("@")) {
                throw GradleException(
                    "sonatypeUsername looks like an email. Use Central Portal USER TOKEN credentials, " +
                        "not your account email/password."
                )
            }

            if (!version.toString().endsWith("SNAPSHOT")) {
                val key = signingKey.trim()
                val pass = signingPassword.trim()
                if (key.isBlank() || pass.isBlank()) {
                    throw GradleException(
                        "Release publishing requires signingKey/signingPassword " +
                            "(or SIGNING_KEY/SIGNING_PASSWORD)."
                    )
                }
            }
        }
    }

    subprojects.forEach { subproject ->
        subproject.tasks.matching { it.name == "publishMavenPublicationToSonatypeRepository" }.configureEach {
            dependsOn(rootProject.tasks.named("validateCentralConfig"))
        }
    }

    register("releaseToCentral") {
        description = "Publishes all subprojects to Sonatype for Maven Central"
        group = "publishing"
        dependsOn(subprojects.map { it.tasks.named("publishMavenPublicationToSonatypeRepository") })
        doLast {
            if (sonatypeUsername.isBlank() || sonatypePassword.isBlank()) {
                throw GradleException("Missing SONATYPE_USERNAME/SONATYPE_PASSWORD (or sonatypeUsername/sonatypePassword).")
            }

            // Snapshot uploads go directly to Central snapshots and do not create
            // an OSSRH-staging-api repository to hand off.
            if (version.toString().endsWith("SNAPSHOT")) {
                logger.lifecycle("Skipping Central Portal handoff for SNAPSHOT version {}", version)
                return@doLast
            }

            val namespace = centralPortalNamespace
            val authValue = Base64.getEncoder()
                .encodeToString("${sonatypeUsername}:${sonatypePassword}".toByteArray(Charsets.UTF_8))
            val endpoint = URI.create(
                "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/$namespace"
            ).toURL()
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $authValue")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                outputStream.use { }
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val responseBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                }.getOrNull().orEmpty()
                throw GradleException(
                    "Central Portal handoff failed (HTTP $responseCode). " +
                        "Check namespace/token and retry. Response: $responseBody"
                )
            }
        }
    }
    runServer {
        dependsOn(shadowJar)
        minecraftVersion("1.21.4")
    }
}

artifacts {
    add("archives", tasks.named("shadowJar"))
}