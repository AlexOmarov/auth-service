plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    `maven-publish`
}

detekt {
    config.setFrom(files("$rootDir/detekt-config.yml"))
}

dependencies {
    detektPlugins(libs.detekt.ktlint)

    api(libs.springdoc.api)
    api(libs.kotlin.serialization.cbor)
    api(libs.spring.boot.starter.validation)
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
