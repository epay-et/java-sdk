// Spring is compileOnly: the starter only ever runs inside a Spring Boot
// application, which already brings its own Spring versions. Declaring them
// here would put a second opinion about those versions into every consumer.

// -PspringBootVersion=4.1.1 tests against another Boot line, as CI does.
val springBootVersion: String = providers.gradleProperty("springBootVersion")
    .getOrElse(libs.versions.spring.boot.get())
val springBootBom = "org.springframework.boot:spring-boot-dependencies:$springBootVersion"

dependencies {
    api(project(":epay-java"))

    compileOnly(platform(springBootBom))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    annotationProcessor(platform(springBootBom))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(platform(springBootBom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-webmvc")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "com.epayethiopia.epay.spring",
            "Implementation-Title" to "epay-spring-boot-starter",
            "Implementation-Version" to project.version,
        )
    }
}

mavenPublishing {
    coordinates(artifactId = "epay-spring-boot-starter")
    pom {
        name = "ePay Spring Boot Starter"
        description = "Spring Boot auto-configuration for the ePay Business API client"
    }
}
