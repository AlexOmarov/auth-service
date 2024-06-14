@file:Suppress("UnstableApiUsage")

rootProject.name = "auth-service"

include("auth-service-app", "auth-service-api")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}