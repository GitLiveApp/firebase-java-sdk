import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Locale

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.publish)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kotlinter)
    alias(libs.plugins.ben.manes.versions)
}

group = "dev.gitlive"
version = project.property("version") as String

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

val aar by configurations.creating

val copyAars by tasks.registering(Copy::class) {
    mkdir("build/aar")
    from(configurations["aar"]) {
        include("*.aar")
    }
    into("build/aar")
}

val extractClasses by tasks.registering(Copy::class) {
    dependsOn(copyAars)
    configurations["aar"].forEach { aarFile ->
        copy {
            from(zipTree(aarFile))
            include("classes.jar")
            fileMode = 0b01110110000
            rename("classes.jar", aarFile.nameWithoutExtension + ".jar")
            into("build/jar")
        }
    }
}

tasks {
    compileKotlin {
        dependsOn(extractClasses)
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    compileTestKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    withType<Sign>().configureEach {
        onlyIf { !project.gradle.startParameter.taskNames.any { "MavenLocal" in it } }
    }
    javadoc {
        exclude("android/**", "libcore/util/**")
    }
}

val jar by tasks.getting(Jar::class) {
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter {
            it.path.startsWith("${projectDir.path}${File.separator}build${File.separator}jar")
        }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val sourceSets = project.the<SourceSetContainer>()

val cleanLibs by tasks.creating(Delete::class) {
    delete("$${layout.buildDirectory.asFile.get().path}/libs")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates("dev.gitlive", "firebase-java-sdk", project.version.toString())

    pom {
        name.set("Firebase Java SDK")
        description.set("A pure Java port of the Firebase Android SDK for client-side Java environments.")
        url.set("https://github.com/GitLiveApp/firebase-java-sdk")
        inceptionYear.set("2023")

        scm {
            url.set("https://github.com/GitLiveApp/firebase-java-sdk")
            connection.set("scm:git:https://github.com/GitLiveApp/firebase-java-sdk.git")
            developerConnection.set("scm:git:https://github.com/GitLiveApp/firebase-java-sdk.git")
            tag.set("HEAD")
        }

        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/GitLiveApp/firebase-java-sdk/issues")
        }

        developers {
            developer {
                name.set("Nicholas Bransby-Williams")
                email.set("nbransby@gmail.com")
            }
        }

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
                comments.set("A business-friendly OSS license")
            }
        }
    }
}

dependencies {
    compileOnly(libs.robolectric.android.all)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.kotlinx.coroutines.swing)
    testImplementation(libs.kotlinx.coroutines.test)
    // firebase aars
    aar(platform(libs.google.firebase.bom))
    aar(libs.google.firebase.firestore)
    aar(libs.google.firebase.functions)
    aar(libs.google.firebase.database)
    aar(libs.google.firebase.config)
    aar(libs.google.firebase.installations)
    aar(libs.google.firebase.storage)
    // extracted aar dependencies
    api(fileTree(mapOf("dir" to "build/jar", "include" to listOf("*.jar"))))
    // polyfill dependencies
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xerial.sqlite.jdbc)
    // firebase dependencies
    implementation(libs.javax.inject)
    implementation(libs.okhttp)
    implementation(libs.io.grpc.protobuf.lite)
    implementation(libs.io.grpc.stub)
    implementation(libs.androidx.collection)
    implementation(libs.io.grpc.okhttp)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.viewmodel)
}

tasks.named("publishToMavenLocal").configure {
    dependsOn(cleanLibs, jar)
}

tasks.named("publish").configure {
    dependsOn(cleanLibs)
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {

    fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA")
            .any { version.uppercase(Locale.ROOT).contains(it) }
        val versionMatch = "^[0-9,.v-]+(-r)?$".toRegex().matches(version)

        return (stableKeyword || versionMatch).not()
    }

    rejectVersionIf {
        isNonStable(candidate.version)
    }

    checkForGradleUpdate = true
    outputFormatter = "plain,html"
    outputDir = "build/dependency-reports"
    reportfileName = "dependency-updates"
}
