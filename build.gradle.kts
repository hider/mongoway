plugins {
    val kotlinVersion = "2.1.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    id("org.springframework.boot") version "3.5.7"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.2"
    `java-test-fixtures`
    jacoco
    application
}

group = "io.github.hider"
version = "0.0.3"
description = "MongoWay is a Database Change Management Tool for MongoDB"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.shell:spring-shell-starter-jni")
    implementation(kotlin("reflect"))
    implementation("org.mongodb:bson-kotlin:5.6.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.shell:spring-shell-starter-test")
    testImplementation("org.springframework:spring-core-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.shell:spring-shell-dependencies:3.4.1")
    }
}

application {
    mainClass = "io.github.hider.mongoway.MongoWayApplicationKt"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dspring.profiles.active=shell",
    )
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

springBoot {
    buildInfo()
}

graalvmNative {
    binaries {
        named("test") {
            // see https://youtrack.jetbrains.com/issue/KT-60211#focus=Comments-27-7877383.0-0
            // see https://github.com/oracle/graal/issues/6957#issuecomment-1839327264
            buildArgs.add("--strict-image-heap")
        }
    }
}

tasks {
    bootBuildImage {
        imageName = "ghcr.io/hider/mongoway:${project.version}-native"
        environment.set(
            mapOf(
                // See Dockerfile and https://paketo.io/docs/howto/configuration/#applying-custom-labels
                "BP_OCI_TITLE" to "MongoWay",
                "BP_OCI_DESCRIPTION" to "MongoWay is a Database Change Management Tool for MongoDB",
                "BP_OCI_URL" to "https://github.com/hider/mongoway",
                "BP_OCI_SOURCE" to "https://github.com/hider/mongoway",
                "BP_OCI_LICENSES" to "GPL-3.0-or-later",
                // see https://paketo.io/docs/howto/configuration/#image-embedded-environment-variables
                "BPE_SPRING_PROFILES_ACTIVE" to "shell",
            )
        )
    }

    jacocoTestReport {
        reports {
            xml.required = true
        }
    }

    withType<Test> {
        useJUnitPlatform()
        jvmArgs("-javaagent:${mockitoAgent.asPath}")
    }
}

tasks.register<Exec>("dockerBuild") {
    dependsOn(tasks.installDist)
    group = "build"
    description = "Builds the Docker image for MongoWay"
    commandLine("docker", "build", "--tag", "ghcr.io/hider/mongoway:${project.version}-alpine", ".")
}
