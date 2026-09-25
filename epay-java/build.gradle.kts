// No runtime dependencies: HTTP is java.net.http and JSON is handled
// internally, so this jar cannot conflict with anything on the classpath.

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "com.epayethiopia.epay",
            "Implementation-Title" to "epay-java",
            "Implementation-Version" to project.version,
        )
    }
}

mavenPublishing {
    coordinates(artifactId = "epay-java")
}

// Json and RetryAfter are public only so other SDK packages can reach them.
tasks.javadoc {
    exclude("**/internal/**")
}
