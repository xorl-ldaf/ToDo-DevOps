plugins {
    `java-library`
}

base {
    archivesName.set("todo-out-messaging-kafka")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    api(project(":core:application"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.jackson.databind)
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)
    implementation(libs.spring.kafka)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
