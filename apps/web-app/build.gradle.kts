plugins {
    alias(libs.plugins.spring.boot)
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(platform(libs.spring.boot.bom))

    constraints {
        implementation(libs.tomcat.core) {
            because("Keeps embedded Tomcat on a fixed 11.0.x patch level for HIGH/CRITICAL CVEs")
        }
        implementation(libs.tomcat.el) {
            because("Keeps embedded Tomcat artifacts on the same fixed 11.0.x patch level")
        }
        implementation(libs.tomcat.websocket) {
            because("Keeps embedded Tomcat artifacts on the same fixed 11.0.x patch level")
        }
        implementation(libs.jackson3.core) {
            because("Fixes GHSA-2m67-wjpj-xhg9")
        }
    }

    implementation(project(":core:application"))
    implementation(project(":adapters:out:persistence-jpa"))
    implementation(project(":adapters:out:messaging-kafka"))
    implementation(project(":adapters:out:messaging-telegram"))
    implementation(project(":adapters:in:messaging-kafka"))
    implementation(project(":adapters:in:web-rest"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.spring.kafka)
    runtimeOnly(libs.micrometer.registry.prometheus)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    exclude("**/*IntegrationTest.class", "**/*IT.class")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs Docker/Testcontainers-backed integration tests."
    group = "verification"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    shouldRunAfter(tasks.test)

    include("**/*IntegrationTest.class", "**/*IT.class")
    useJUnitPlatform()
}
