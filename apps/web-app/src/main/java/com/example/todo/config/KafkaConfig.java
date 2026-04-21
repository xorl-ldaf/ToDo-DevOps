package com.example.todo.config;

import com.example.todo.adapter.in.kafka.KafkaReminderScheduledEventConsumer;
import com.example.todo.adapter.out.kafka.KafkaReminderScheduledEventPublisher;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.example.todo.application.event.ReminderScheduledEventV1;
import com.example.todo.application.port.out.PublishReminderScheduledEventPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@EnableConfigurationProperties(TodoKafkaProperties.class)
public class KafkaConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    KafkaAdmin kafkaAdmin(TodoKafkaProperties properties) {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, normalizeBootstrapServers(properties.getBootstrapServers()));
        return new KafkaAdmin(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    org.apache.kafka.clients.admin.NewTopic reminderScheduledV1Topic(TodoKafkaProperties properties) {
        return TopicBuilder.name(properties.getTopics().getReminderScheduledV1())
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    ProducerFactory<String, String> reminderScheduledProducerFactory(TodoKafkaProperties properties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, normalizeBootstrapServers(properties.getBootstrapServers()));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    KafkaTemplate<String, String> reminderScheduledKafkaTemplate(
            ProducerFactory<String, String> reminderScheduledProducerFactory
    ) {
        return new KafkaTemplate<>(reminderScheduledProducerFactory);
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    ConsumerFactory<String, String> reminderScheduledConsumerFactory(
            TodoKafkaProperties properties
    ) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, normalizeBootstrapServers(properties.getBootstrapServers()));
        config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getConsumerGroupId());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    ConcurrentKafkaListenerContainerFactory<String, String> reminderScheduledKafkaListenerContainerFactory(
            ConsumerFactory<String, String> reminderScheduledConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(reminderScheduledConsumerFactory);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    PublishReminderScheduledEventPort publishReminderScheduledEventPort(
            KafkaTemplate<String, String> reminderScheduledKafkaTemplate,
            ObjectMapper objectMapper,
            TodoKafkaProperties properties
    ) {
        return new KafkaReminderScheduledEventPublisher(
                reminderScheduledKafkaTemplate,
                objectMapper,
                properties.getTopics().getReminderScheduledV1()
        );
    }

    @Bean
    @ConditionalOnMissingBean(PublishReminderScheduledEventPort.class)
    PublishReminderScheduledEventPort noOpPublishReminderScheduledEventPort() {
        return new NoOpReminderScheduledEventPublisher();
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    KafkaReminderScheduledEventConsumer kafkaReminderScheduledEventConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        return new KafkaReminderScheduledEventConsumer(objectMapper, meterRegistry, clock);
    }

    private String normalizeBootstrapServers(String value) {
        return value.replace("PLAINTEXT://", "");
    }
}
