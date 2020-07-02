import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.3.31"
    id("application")
    id("maven-publish")
    id("com.github.johnrengelman.shadow") version "4.0.2"
}

group = "com.genomealmanac.chipseq"
version = "1.0.7"
val artifactID = "chipseq-workflow"

repositories {
    jcenter()
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("io.krews", "krews", "0.10.6")
}

application {
    mainClassName = "ChipSeqWorkflowKt"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val shadowJar: ShadowJar by tasks
shadowJar.apply {
    archiveBaseName.set(artifactID)
    archiveClassifier.set("exec")
    destinationDirectory.set(file("build"))
}

val publicationName = "chipseq-workflow"
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/krews-community/chipseq-workflow")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USER")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("gpr") {
            artifactId = artifactID
            from(components["java"])
            artifact(shadowJar)
        }
    }
}