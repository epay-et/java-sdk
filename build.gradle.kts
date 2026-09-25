import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.maven.publish) apply false
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.vanniktech.maven.publish")

    extensions.configure<JavaPluginExtension> {
        // Compiled with --release rather than a toolchain, so any JDK 17+ can
        // build the project while the bytecode and API surface stay at 17.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 17
        options.encoding = "UTF-8"
        // -processing is off because the configuration processor finds no
        // annotations to claim in some rounds, which is expected.
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror", "-parameters"))
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addBooleanOption("Xdoclint:all,-missing", true)
            addStringOption("Xwerror", "-quiet")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    extensions.configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        // Signing is required by Maven Central but not for a local build, so
        // it is only switched on when the release workflow supplies a key.
        if (providers.gradleProperty("signingInMemoryKey").isPresent) {
            signAllPublications()
        }
    }
}
