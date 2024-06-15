plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    `maven-publish`
}

detekt {
    config.setFrom(files("$rootDir/detekt-config.yml"))
}

dependencies {
    detektPlugins(libs.detekt.ktlint)

    api(libs.kotlin.serialization.cbor)
    api(libs.swagger.annotations)
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
