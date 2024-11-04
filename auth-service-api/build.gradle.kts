plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    `maven-publish`
}

dependencies {
    detektPlugins(libs.detekt.ktlint)

    api(libs.kotlin.serialization.core)
    api(libs.swagger.annotations)
    api(libs.kotlin.datetime)
}

publishing {
    publications {
        create<MavenPublication>(rootProject.name) {
            from(components["kotlin"])
        }
    }
    repositories {
        maven {
            url = uri(System.getenv("PRIVATE_REPO_URL") ?: "")
            name = "PrivateRepo"
            credentials(HttpHeaderCredentials::class) {
                name = "Token"
                value = System.getenv("PRIVATE_REPO_TOKEN")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

detekt {
    config.setFrom(files("$rootDir/detekt-config.yml"))
}

