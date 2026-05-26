package com.example.todo;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {
    private static final String DOMAIN = "com.example.todo.domain..";
    private static final String APPLICATION = "com.example.todo.application..";
    private static final String ADAPTERS = "com.example.todo.adapter..";
    private static final String WEB_APP_ROOT = "com.example.todo";
    private static final String WEB_APP_CONFIG = "com.example.todo.config..";
    private static final String WEB_DTOS = "com.example.todo.adapter.in.web.dto..";
    private static final String JPA_ENTITIES = "com.example.todo.adapter.out.persistence.entity..";
    private static final String APPLICATION_OUT_PORTS = "com.example.todo.application.port.out..";

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
}
