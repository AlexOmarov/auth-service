@file:Suppress("UnstableApiUsage")

rootProject.name = "auth-service"

include("auth-service-app")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}