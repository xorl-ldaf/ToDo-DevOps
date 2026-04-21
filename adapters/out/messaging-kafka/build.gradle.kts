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
    implementation(libs.spring.kafka)
}

tasks.test {
    useJUnitPlatform()
}
