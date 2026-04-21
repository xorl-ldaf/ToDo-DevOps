plugins {
    `java-library`
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
    implementation(libs.spring.kafka)
    implementation(libs.spring.boot.starter.actuator)
}

tasks.test {
    useJUnitPlatform()
}
