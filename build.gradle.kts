plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.sonarqube)
}

project.allprojects.forEach {
    it.repositories {
        mavenCentral()
    }
}

kotlin {
    jvmToolchain(21) // todo: put 23 when kotlin and detekt will support it
    sourceSets.all {
        languageSettings {
            compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21 // todo: put 23 when kotlin and detekt will support it
    targetCompatibility = JavaVersion.VERSION_21 // todo: put 23 when kotlin and detekt will support it
}

sonar {
    val exclusions = project.properties["test_exclusions"].toString()
    val appDir = project(":auth-service-app").layout.buildDirectory.get().asFile
    val apiDir = project(":auth-service-api").layout.buildDirectory.get().asFile
    val detektFilePath = "/reports/detekt/detekt.xml"

    properties {
        property("sonar.kotlin.detekt.reportPaths", "$appDir$detektFilePath, $apiDir$detektFilePath")
        property("sonar.qualitygate.wait", "true")
        property("sonar.core.codeCoveragePlugin", "jacoco")
        property("sonar.coverage.jacoco.xmlReportPaths", "$appDir/reports/kover/report.xml")
        property("sonar.cpd.exclusions", exclusions)
        property("sonar.jacoco.excludes", exclusions)
        property("sonar.coverage.exclusions", exclusions)
        property("sonar.junit.reportPaths", "$appDir/test-results/test/")
    }
}
