import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask

plugins {
    base
}

subprojects {
    plugins.withId("java") {
        the<org.gradle.api.plugins.JavaPluginExtension>().toolchain {
            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
        }
    }
}

group = "com.example.todo"
version = "0.1.0-SNAPSHOT"

tasks.named<DependencyInsightReportTask>("dependencyInsight") {
    configuration = project(":apps:web-app").configurations.named("runtimeClasspath").get()
}
