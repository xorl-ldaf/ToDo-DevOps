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

extra["tomcat.version"] = "11.0.21"
extra["jackson-bom.version"] = "3.1.1"

tasks.named<DependencyInsightReportTask>("dependencyInsight") {
    configuration = project(":apps:web-app").configurations.named("runtimeClasspath").get()
}
