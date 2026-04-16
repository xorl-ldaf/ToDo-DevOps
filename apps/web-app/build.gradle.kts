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

    implementation(project(":core:application"))
    implementation(project(":adapters:out:persistence-jpa"))
    implementation(project(":adapters:in:web-rest"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)

    implementation(libs.flyway.core)

    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
}

tasks.test {
    useJUnitPlatform()
}