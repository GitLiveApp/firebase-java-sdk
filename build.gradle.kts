
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
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("org.jlleitschuh.gradle.ktlint") version "10.0.0"
    id("com.github.ben-manes.versions") version "0.42.0"
}

group = "dev.gitlive"
version = project.property("version") as String

java {
    withSourcesJar()
    withJavadocJar()
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
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
        }
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
    delete("$buildDir/libs")
}

publishing {

    repositories {
        maven {
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            credentials {
                username = project.findProperty("sonatypeUsername") as String? ?: System.getenv("sonatypeUsername")
                password = project.findProperty("sonatypePassword") as String? ?: System.getenv("sonatypePassword")
            }
        }
    }

    publications {
        create<MavenPublication>("library") {
            from(components["java"])

            pom {
                name.set("firebase-java-sdk")
                description.set("The Firebase Java SDK is a pure java port of the Firebase Android SDK to run in clientside java environments such as the desktop.")
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
    }
}

/**
 * List of aar files to include in the jar. Some jars are being omitted because they are not needed for the JVM.
 * - lifecycle-*: exclude lifecycle libs due to https://github.com/GitLiveApp/firebase-java-sdk/pull/15 - remove the exclude  once the dependencies in the aars are updated to the required version
 * - savedstate: Excluded due to this library already being included as part of the compose mutliplatform dependencies. It does not seem to be directly needed by the firebase libraries.
 */
val includeList = listOf(
    "activity-*.jar",
    "asynclayoutinflater-*.jar",
    "coordinatorlayout-*.jar",
    "core-*.jar",
    "core-runtime-*.jar",
    "cursoradapter-*.jar",
    "customview-*.jar",
    "documentfile-*.jar",
    "drawerlayout-*.jar",
    "firebase-abt-*.jar",
    "firebase-appcheck-*.jar",
    "firebase-appcheck-interop-*.jar",
    "firebase-auth-interop-*.jar",
    "firebase-common-*.jar",
    "firebase-common-*.jar",
    "firebase-common-ktx-*.jar",
    "firebase-common-ktx-*.jar",
    "firebase-components-*.jar",
    "firebase-components-*.jar",
    "firebase-config-*.jar",
    "firebase-config-interop-*.jar",
    "firebase-database-*.jar",
    "firebase-database-collection-*.jar",
    "firebase-encoders-json-*.jar",
    "firebase-firestore-*.jar",
    "firebase-functions-*.jar",
    "firebase-iid-*.jar",
    "firebase-iid-interop-*.jar",
    "firebase-installations-*.jar",
    "firebase-installations-interop-*.jar",
    "firebase-measurement-connector-*.jar",
    "firebase-storage-*.jar",
    "fragment-*.jar",
    "fragment-*.jar",
    "grpc-android-*.jar",
    "interpolator-*.jar",
    "legacy-support-core-ui-*.jar",
    "legacy-support-core-utils-*.jar",
    "loader-*.jar",
    "localbroadcastmanager-*.jar",
    "play-services-base-*.jar",
    "play-services-basement-*.jar",
    "play-services-basement-*.jar",
    "play-services-cloud-messaging-*.jar",
    "play-services-stats-*.jar",
    "play-services-tasks-*.jar",
    "play-services-tasks-*.jar",
    "print-*.jar",
    "protolite-well-known-types-*.jar",
    "slidingpanelayout-*.jar",
    "swiperefreshlayout-*.jar",
    "versionedparcelable-*.jar",
    "viewpager-*.jar",
)

dependencies {
    compileOnly("org.robolectric:android-all:12.1-robolectric-8229987")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    testImplementation("org.mockito:mockito-core:5.12.0")
    // firebase aars
    aar("com.google.firebase:firebase-firestore:24.10.0")
    aar("com.google.firebase:firebase-functions:20.4.0")
    aar("com.google.firebase:firebase-database:20.3.0")
    aar("com.google.firebase:firebase-config:21.6.0")
    aar("com.google.firebase:firebase-installations:17.2.0")
    aar("com.google.firebase:firebase-storage:21.0.0")
    // extracted aar dependencies
    api(fileTree(mapOf("dir" to "build/jar", "include" to includeList)))
    // polyfill dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    // firebase dependencies
    implementation("javax.inject:javax.inject:1")
    implementation("com.squareup.okhttp3:okhttp:3.12.13")
    implementation("io.grpc:grpc-protobuf-lite:1.52.1")
    implementation("io.grpc:grpc-stub:1.52.1")
    implementation("androidx.collection:collection:1.2.0")
    implementation("io.grpc:grpc-okhttp:1.52.1")
    implementation("androidx.lifecycle:lifecycle-common:2.8.0-rc01")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.0-rc01")
}

tasks.named("publishToMavenLocal").configure {
    dependsOn(cleanLibs, jar)
}

tasks.named("publish").configure {
    dependsOn(cleanLibs)
}

ktlint {
    version.set("0.41.0")
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {

    fun isNonStable(version: String): Boolean {
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
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
