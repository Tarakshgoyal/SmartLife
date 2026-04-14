package com.smartlife.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        var props = new HashMap<String, Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }


    @Value("${smartlife.kafka.topics.document-processed}")
    private String documentProcessedTopic;

    @Value("${smartlife.kafka.topics.expense-created}")
    private String expenseCreatedTopic;

    @Value("${smartlife.kafka.topics.reminder-triggered}")
    private String reminderTriggeredTopic;

    @Value("${smartlife.kafka.topics.health-alert}")
    private String healthAlertTopic;

    @Bean
    public NewTopic documentProcessedTopic() {
        return TopicBuilder.name(documentProcessedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic expenseCreatedTopic() {
        return TopicBuilder.name(expenseCreatedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reminderTriggeredTopic() {
        return TopicBuilder.name(reminderTriggeredTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic healthAlertTopic() {
        return TopicBuilder.name(healthAlertTopic).partitions(3).replicas(1).build();
    }
}
