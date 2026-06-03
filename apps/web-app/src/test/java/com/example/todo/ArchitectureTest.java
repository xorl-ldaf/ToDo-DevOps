package com.example.todo;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureTest {
    private static final String DOMAIN = "com.example.todo.domain..";
    private static final String APPLICATION = "com.example.todo.application..";
    private static final String ADAPTERS = "com.example.todo.adapter..";
    private static final String INBOUND_ADAPTERS = "com.example.todo.adapter.in..";
    private static final String OUTBOUND_ADAPTERS = "com.example.todo.adapter.out..";
    private static final String WEB_REST_ADAPTER = "com.example.todo.adapter.in.web..";
    private static final String MESSAGING_KAFKA_IN_ADAPTER = "com.example.todo.adapter.in.kafka..";
    private static final String PERSISTENCE_JPA_ADAPTER = "com.example.todo.adapter.out.persistence..";
    private static final String MESSAGING_KAFKA_OUT_ADAPTER = "com.example.todo.adapter.out.kafka..";
    private static final String MESSAGING_TELEGRAM_OUT_ADAPTER = "com.example.todo.adapter.out.telegram..";
    private static final String WEB_APP_ROOT = "com.example.todo";
    private static final String WEB_APP_CONFIG = "com.example.todo.config..";
    private static final String WEB_DTOS = "com.example.todo.adapter.in.web.dto..";
    private static final String JPA_ENTITIES = "com.example.todo.adapter.out.persistence.entity..";
    private static final String APPLICATION_OUT_PORTS = "com.example.todo.application.port.out..";
    private static final Path REPO_ROOT = locateRepositoryRoot();
    private static final Pattern PROJECT_DEPENDENCY = Pattern.compile("project\\(\"([^\"]+)\"\\)");
    private static final Set<String> CORE_INWARD_DEPENDENCIES = Set.of(":core:domain");
    private static final Set<String> ADAPTER_INWARD_DEPENDENCIES = Set.of(":core:domain", ":core:application");
    private static final Set<String> WEB_APP_COMPOSITION_ROOT_DEPENDENCIES = Set.of(
            ":core:domain",
            ":core:application",
            ":adapters:out:persistence-jpa",
            ":adapters:out:messaging-kafka",
            ":adapters:out:messaging-telegram",
            ":adapters:in:messaging-kafka",
            ":adapters:in:web-rest"
    );

    private static final String[] DOMAIN_FORBIDDEN_DEPENDENCIES = {
            APPLICATION,
            ADAPTERS,
            WEB_APP_CONFIG,
            "org.springframework..",
            "jakarta.persistence..",
            "javax.persistence..",
            "org.hibernate..",
            "org.springframework.kafka..",
            "org.apache.kafka..",
            "org.springframework.http..",
            "org.springframework.web..",
            "java.net.http..",
            "java.sql..",
            "javax.sql..",
            "org.postgresql..",
            "jakarta.servlet..",
            "javax.servlet..",
            "org.telegram..",
            "com.pengrad.telegrambot.."
    };

    private static final String[] APPLICATION_FORBIDDEN_DEPENDENCIES = {
            ADAPTERS,
            WEB_APP_CONFIG,
            "org.springframework..",
            "jakarta.persistence..",
            "javax.persistence..",
            "org.hibernate..",
            "org.springframework.kafka..",
            "org.apache.kafka..",
            "org.springframework.http..",
            "org.springframework.web..",
            "java.net.http..",
            "java.sql..",
            "javax.sql..",
            "org.postgresql..",
            "jakarta.servlet..",
            "javax.servlet..",
            "org.telegram..",
            "com.pengrad.telegrambot.."
    };

    private static final com.tngtech.archunit.core.domain.JavaClasses productionClasses =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages("com.example.todo");

    @Test
    void gradle_module_dependencies_follow_onion_direction() throws IOException {
        assertThat(projectDependencies("core/domain"))
                .as("domain stays independent from Spring/JPA/adapters/modules")
                .isEmpty();

        assertThat(projectDependencies("core/application"))
                .as("application may depend inward on domain only")
                .contains(":core:domain")
                .allMatch(CORE_INWARD_DEPENDENCIES::contains);

        for (String adapterModule : List.of(
                "adapters/in/web-rest",
                "adapters/in/messaging-kafka",
                "adapters/out/persistence-jpa",
                "adapters/out/messaging-kafka",
                "adapters/out/messaging-telegram"
        )) {
            assertThat(projectDependencies(adapterModule))
                    .as(adapterModule + " may depend inward on application/domain only")
                    .contains(":core:application")
                    .allMatch(ADAPTER_INWARD_DEPENDENCIES::contains);
        }

        assertThat(projectDependencies("apps/web-app"))
                .as("web-app is the composition root and may wire application with adapters")
                .contains(
                        ":core:application",
                        ":adapters:out:persistence-jpa",
                        ":adapters:out:messaging-kafka",
                        ":adapters:out:messaging-telegram",
                        ":adapters:in:messaging-kafka",
                        ":adapters:in:web-rest"
                )
                .allMatch(WEB_APP_COMPOSITION_ROOT_DEPENDENCIES::contains);
    }

    @Test
    void onion_layers_point_inward_and_web_app_is_the_composition_root() {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(DOMAIN)
                .layer("Application").definedBy(APPLICATION)
                .layer("Adapters").definedBy(ADAPTERS)
                .layer("WebApp").definedBy(WEB_APP_ROOT, WEB_APP_CONFIG)
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapters", "WebApp")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "WebApp")
                .whereLayer("Adapters").mayOnlyBeAccessedByLayers("WebApp")
                .whereLayer("WebApp").mayNotBeAccessedByAnyLayer()
                .as("domain/application are inward layers, adapters depend inward, web-app wires everything");

        rule.check(productionClasses);
    }

    @Test
    void domain_does_not_depend_on_frameworks_or_adapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(DOMAIN_FORBIDDEN_DEPENDENCIES)
                .as("domain is a pure data model without Spring, JPA, Kafka, Telegram, web, persistence or adapters");

        rule.check(productionClasses);
    }

    @Test
    void application_does_not_depend_on_adapters_or_delivery_technology() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION_FORBIDDEN_DEPENDENCIES)
                .as("application services/use cases do not depend on adapters, Spring MVC, JPA, KafkaTemplate or Telegram clients");

        rule.check(productionClasses);
    }

    @Test
    void web_dtos_are_not_referenced_from_domain_or_application() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(DOMAIN, APPLICATION)
                .should().dependOnClassesThat().resideInAPackage(WEB_DTOS)
                .as("web DTOs stay in the web adapter and do not leak into domain/application");

        rule.check(productionClasses);
    }

    @Test
    void jpa_entities_are_not_referenced_from_domain_or_application() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(DOMAIN, APPLICATION)
                .should().dependOnClassesThat().resideInAPackage(JPA_ENTITIES)
                .as("JPA entities stay in the persistence adapter and do not leak into domain/application");

        rule.check(productionClasses);
    }

    @Test
    void adapters_do_not_depend_on_the_web_app_composition_root() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ADAPTERS)
                .should().dependOnClassesThat().resideInAnyPackage(WEB_APP_ROOT, WEB_APP_CONFIG)
                .as("adapters may depend inward on application/domain, but not outward on the web-app composition root");

        rule.check(productionClasses);
    }

    @Test
    void inbound_adapters_do_not_depend_on_outbound_adapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(INBOUND_ADAPTERS)
                .should().dependOnClassesThat().resideInAPackage(OUTBOUND_ADAPTERS)
                .as("inbound adapters call application use cases and do not depend on persistence or publisher adapters");

        rule.check(productionClasses);
    }

    @Test
    void outbound_adapters_do_not_depend_on_inbound_adapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(OUTBOUND_ADAPTERS)
                .should().dependOnClassesThat().resideInAPackage(INBOUND_ADAPTERS)
                .as("outbound adapters implement application ports and do not depend on REST or consumer adapters");

        rule.check(productionClasses);
    }

    @Test
    void adapter_modules_do_not_depend_on_each_other_sideways() {
        noClasses()
                .that().resideInAPackage(WEB_REST_ADAPTER)
                .should().dependOnClassesThat().resideInAnyPackage(
                        MESSAGING_KAFKA_IN_ADAPTER,
                        PERSISTENCE_JPA_ADAPTER,
                        MESSAGING_KAFKA_OUT_ADAPTER,
                        MESSAGING_TELEGRAM_OUT_ADAPTER
                )
                .as("web-rest adapter talks to application use cases, not to Kafka, persistence, or Telegram adapters")
                .check(productionClasses);

        noClasses()
                .that().resideInAPackage(MESSAGING_KAFKA_IN_ADAPTER)
                .should().dependOnClassesThat().resideInAnyPackage(
                        WEB_REST_ADAPTER,
                        PERSISTENCE_JPA_ADAPTER,
                        MESSAGING_KAFKA_OUT_ADAPTER,
                        MESSAGING_TELEGRAM_OUT_ADAPTER
                )
                .as("Kafka inbound adapter talks to application use cases, not to web or outbound adapters")
                .check(productionClasses);

        noClasses()
                .that().resideInAPackage(PERSISTENCE_JPA_ADAPTER)
                .should().dependOnClassesThat().resideInAnyPackage(
                        WEB_REST_ADAPTER,
                        MESSAGING_KAFKA_IN_ADAPTER,
                        MESSAGING_KAFKA_OUT_ADAPTER,
                        MESSAGING_TELEGRAM_OUT_ADAPTER
                )
                .as("persistence adapter implements ports and does not call web, Kafka publisher, or Telegram adapters")
                .check(productionClasses);

        noClasses()
                .that().resideInAPackage(MESSAGING_KAFKA_OUT_ADAPTER)
                .should().dependOnClassesThat().resideInAnyPackage(
                        WEB_REST_ADAPTER,
                        MESSAGING_KAFKA_IN_ADAPTER,
                        PERSISTENCE_JPA_ADAPTER,
                        MESSAGING_TELEGRAM_OUT_ADAPTER
                )
                .as("Kafka publisher adapter implements ports and does not call web, consumer, persistence, or Telegram adapters")
                .check(productionClasses);

        noClasses()
                .that().resideInAPackage(MESSAGING_TELEGRAM_OUT_ADAPTER)
                .should().dependOnClassesThat().resideInAnyPackage(
                        WEB_REST_ADAPTER,
                        MESSAGING_KAFKA_IN_ADAPTER,
                        PERSISTENCE_JPA_ADAPTER,
                        MESSAGING_KAFKA_OUT_ADAPTER
                )
                .as("Telegram adapter implements ports and does not call web, Kafka, or persistence adapters")
                .check(productionClasses);
    }

    @Test
    void outbound_adapters_implement_application_output_ports() {
        ArchRule persistenceAdapters = classes()
                .that().resideInAPackage("com.example.todo.adapter.out.persistence.adapter..")
                .should().implement(resideInAPackage(APPLICATION_OUT_PORTS));

        ArchRule kafkaPublishers = classes()
                .that().resideInAPackage("com.example.todo.adapter.out.kafka..")
                .and().haveSimpleNameEndingWith("Publisher")
                .should().implement(resideInAPackage(APPLICATION_OUT_PORTS));

        ArchRule telegramSenders = classes()
                .that().resideInAPackage("com.example.todo.adapter.out.telegram..")
                .and().haveSimpleNameEndingWith("Sender")
                .should().implement(resideInAPackage(APPLICATION_OUT_PORTS));

        persistenceAdapters.check(productionClasses);
        kafkaPublishers.check(productionClasses);
        telegramSenders.check(productionClasses);
    }

    private static List<String> projectDependencies(String modulePath) throws IOException {
        String buildFile = Files.readString(REPO_ROOT.resolve(modulePath).resolve("build.gradle.kts"));
        return PROJECT_DEPENDENCY.matcher(buildFile)
                .results()
                .map(MatchResult::group)
                .map(dependency -> dependency.substring("project(\"".length(), dependency.length() - "\")".length()))
                .toList();
    }

    private static Path locateRepositoryRoot() {
        Path currentPath = Path.of("").toAbsolutePath();
        while (currentPath != null) {
            if (Files.exists(currentPath.resolve("settings.gradle.kts"))) {
                return currentPath;
            }
            currentPath = currentPath.getParent();
        }
        throw new IllegalStateException("Repository root with settings.gradle.kts was not found");
    }
}
