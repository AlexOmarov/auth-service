plugins {
    kotlin("jvm")
    application

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

dependencies {
    detektPlugins(libs.detekt.ktlint)

    implementation(project(":auth-service-api"))

    implementation(libs.bundles.database)
    implementation(libs.bundles.kafka)
    implementation(libs.bundles.kotlinx)
    implementation(libs.bundles.logback)
    implementation(libs.bundles.micrometer)
    implementation(libs.bundles.postgres)
    implementation(libs.bundles.redis)
    implementation(libs.bundles.rsocket)
    implementation(libs.bundles.shedlock)
    implementation(libs.bundles.web)

    implementation(libs.otel.otlp.exporter)
    implementation(libs.jwt)

    testImplementation(libs.bundles.test)
}

tasks {
    shadowJar {
        mergeServiceFiles() // for micrometer reactor context propagation
    }
}

tasks.register("generateBuildInfo") {
    group = "build"
    description = "Generates build-info.properties file with build metadata"

    doLast {
        val file = file("${layout.buildDirectory.asFile.get().path}/resources/main/META-INF/build-info.properties")
        file.parentFile.mkdirs()
        file.writeText(
            """
                build.version=${project.version}
                build.group=${project.group}
                build.artifact=${project.name}
            """.trimIndent()
        )
    }
}

tasks {
    jar {
        dependsOn("generateBuildInfo")
    }

    test {
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
}

detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
}

application {
    mainClass.set("ru.somarov.auth.AppKt")
}

ktor {
    fatJar {
        archiveFileName.set("app.jar")
    }
}

kover {
    useJacoco()
    reports {
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
        }
    }
}

