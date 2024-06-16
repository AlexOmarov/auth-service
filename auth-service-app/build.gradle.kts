plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

detekt {
    config.setFrom(files("$rootDir/detekt-config.yml"))
}

dependencies {
    implementation(project(":auth-service-api"))

    detektPlugins(libs.detekt.ktlint)

    implementation(libs.bundles.web)
    implementation(libs.bundles.kafka)
    implementation(libs.bundles.database)

    implementation(libs.bundles.postgres)
    implementation(libs.bundles.redis)
    implementation(libs.bundles.micrometer)
    implementation(libs.bundles.shedlock)

    implementation(libs.rsocket.micrometer)
    implementation(libs.logback)
    implementation(libs.logback.logstash)
    implementation(libs.logback.otel)
    implementation(libs.otel.otlp)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.launcher)

}

repositories {
    mavenCentral()
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

kover {
    useJacoco()
    reports {
        verify {
            rule {
                minBound(0)
            }
        }
        total {
            filters {
                excludes {
                    classes(
                        project.properties["test_exclusions"]
                            .toString()
                            .replace("/", ".")
                            .split(",")
                    )
                }
            }

            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
        }
    }
}

