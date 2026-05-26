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
    implementation(project(":core:application"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)

    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")

    testCompileOnly("org.projectlombok:lombok:1.18.32")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.32")
}

tasks.test {
    useJUnitPlatform()
    exclude("**/*IntegrationTest.class", "**/*IT.class")
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs Docker/Testcontainers-backed persistence integration tests."
    group = "verification"

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    shouldRunAfter(tasks.test)

    include("**/*IntegrationTest.class", "**/*IT.class")
    useJUnitPlatform()
}
