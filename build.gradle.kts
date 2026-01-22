plugins {
    val kotlinVersion = "2.3.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.4"
    `java-test-fixtures`
    jacoco
    application
}

group = "io.github.hider"
version = "0.1.0"
description = "MongoWay is a Database Change Management Tool for MongoDB"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

private val mockitoAgent: Configuration by configurations.creating

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.shell:spring-shell-starter")
    implementation("org.mongodb:bson-kotlin:5.6.2")

    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.shell:spring-shell-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mongodb")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.shell:spring-shell-dependencies:4.0.1")
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
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
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

private val imageBaseName = "ghcr.io/hider/mongoway:${project.version}"

tasks {
    bootBuildImage {
        imageName = "${imageBaseName}-native"
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

    val aotOpens = listOf(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
    )

    processAot {
        jvmArgs = aotOpens
    }

    processTestAot {
        jvmArgs = aotOpens
    }

    withType<Test> {
        useJUnitPlatform()
        systemProperties("user.language" to "en")
        jvmArgs("-javaagent:${mockitoAgent.asPath}")
    }
}

tasks.register<Exec>("buildImageAlpine") {
    inputs.files(tasks.installDist)
    group = "docker"
    description = "Builds the Docker image for MongoWay with Alpine Linux and Temurin JRE."
    commandLine("docker", "build", "--tag", "${imageBaseName}-alpine", ".")
}

tasks.register<Exec>("buildImageAlpaquita") {
    inputs.files(tasks.installDist)
    group = "docker"
    description = "Builds the Docker image for MongoWay with Alpaquita Linux and Liberica JRE."
    commandLine("docker", "build", "--tag", "${imageBaseName}-alpaquita", "--file", "Alpaquita.Dockerfile", ".")
}

tasks.register("printGithubActionOutput") {
    group = "docker"
    println("version=${project.version}")
    print("tags=")
    arrayOf("alpine", "alpaquita").forEach {
        print("$imageBaseName-$it ")
    }
    println()
}
