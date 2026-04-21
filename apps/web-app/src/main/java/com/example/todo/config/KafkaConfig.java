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
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@EnableConfigurationProperties(TodoKafkaProperties.class)
public class KafkaConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

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
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.RETRIES_CONFIG, properties.getProducer().getRetries());
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, toIntMillis(properties.getProducer().getRetryBackoff()));
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, toIntMillis(properties.getProducer().getRequestTimeout()));
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, toIntMillis(properties.getProducer().getDeliveryTimeout()));
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
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
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    ConcurrentKafkaListenerContainerFactory<String, String> reminderScheduledKafkaListenerContainerFactory(
            ConsumerFactory<String, String> reminderScheduledConsumerFactory,
            CommonErrorHandler reminderScheduledKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(reminderScheduledConsumerFactory);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(reminderScheduledKafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    PublishReminderScheduledEventPort publishReminderScheduledEventPort(
            KafkaTemplate<String, String> reminderScheduledKafkaTemplate,
            ObjectMapper objectMapper,
            TodoKafkaProperties properties,
            MeterRegistry meterRegistry
    ) {
        return new KafkaReminderScheduledEventPublisher(
                reminderScheduledKafkaTemplate,
                objectMapper,
                properties.getTopics().getReminderScheduledV1(),
                meterRegistry
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

    @Bean
    @ConditionalOnProperty(prefix = "todo.kafka", name = "enabled", havingValue = "true")
    CommonErrorHandler reminderScheduledKafkaErrorHandler(
            TodoKafkaProperties properties,
            MeterRegistry meterRegistry
    ) {
        FixedBackOff backOff = new FixedBackOff(
                toLongMillis(properties.getConsumer().getRetryBackoff()),
                properties.getConsumer().getMaxAttempts()
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((consumerRecord, exception) -> {
            meterRegistry.counter(
                    "todo.reminder.scheduled.events.failed",
                    "topic", consumerRecord.topic(),
                    "reason", exception.getClass().getSimpleName()
            ).increment();
            log.error(
                    "Kafka reminder scheduled event handling failed topic={} partition={} offset={}",
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    exception
            );
        }, backOff);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                meterRegistry.counter(
                        "todo.reminder.scheduled.events.retries",
                        "topic", record.topic(),
                        "reason", exception.getClass().getSimpleName()
                ).increment()
        );
        return errorHandler;
    }

    private String normalizeBootstrapServers(String value) {
        return value.replace("PLAINTEXT://", "");
    }

    private int toIntMillis(Duration duration) {
        long value = toLongMillis(duration);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Kafka timeout/backoff properties must fit into a 32-bit integer");
        }
        return (int) value;
    }

    private long toLongMillis(Duration duration) {
        Duration actualDuration = duration == null ? null : duration;
        if (actualDuration == null || actualDuration.isNegative() || actualDuration.isZero()) {
            throw new IllegalStateException("Kafka timeout/backoff properties must be positive durations");
        }
        return actualDuration.toMillis();
    }
}
