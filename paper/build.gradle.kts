plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    id("io.papermc.paperweight.userdev") version "1.5.10"
}

dependencies {
    //compileOnly("io.papermc.paper:paper-api:1.20.2-R0.1-SNAPSHOT")
    paperweight.paperDevBundle("1.20.2-R0.1-SNAPSHOT")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
}

tasks {
}