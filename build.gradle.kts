plugins {
    id("scala")
    id("java-library")
    `maven-publish`
}

group   = "eu.webrobot"
version = "0.2.0"

val scalaVersion     = "2.13.12"
val scalaBinaryV     = "2.13"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.scala-lang:scala-library:$scalaVersion")
    testImplementation("org.scalatest:scalatest_$scalaBinaryV:3.2.18")
    testImplementation("org.scala-lang:scala-library:$scalaVersion")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.0")
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "webrobot-plugin-sdk"
            from(components["java"])
            pom {
                name.set("WebRobot Plugin SDK")
                description.set("Public API for building WebRobot ETL plugins — no ETL internals exposed")
                url.set("https://github.com/WebRobot-Ltd/webrobot-plugin-sdk")
                licenses {
                    license {
                        name.set("Proprietary")
                        url.set("https://webrobot.eu")
                    }
                }
            }
        }
    }
    repositories {
        // GitHub Packages registration kept for internal CI; only registered when a token
        // is present so that JitPack and partner-side mavenLocal builds (no token) work.
        // Public consumption of this SDK happens via JitPack.
        val gprToken = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
        if (gprToken != null) {
            maven {
                name = "GitHubPackages"
                url  = uri("https://maven.pkg.github.com/WebRobot-Ltd/webrobot-plugin-sdk")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull ?: "webroboteu"
                    password = gprToken
                }
            }
        }
    }
}
