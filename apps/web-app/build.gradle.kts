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
            because("Fixes CVE-2026-34483 and CVE-2026-34487")
        }
        implementation(libs.jackson3.core) {
            because("Fixes GHSA-2m67-wjpj-xhg9")
        }
    }

    implementation(project(":core:application"))
    implementation(project(":adapters:out:persistence-jpa"))
    implementation(project(":adapters:in:web-rest"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
