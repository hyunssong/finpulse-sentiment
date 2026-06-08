package com.hyunprojects.finpulse.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {

    @Bean
    fun rawArticlesTopic(): NewTopic = TopicBuilder.name("raw-articles")
        .partitions(6)
        .replicas(1)
        .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
        .config(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "1048576")
        .build()

    @Bean
    fun analyzedArticlesTopic(): NewTopic = TopicBuilder.name("analyzed-articles")
        .partitions(6)
        .replicas(1)
        .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
        .config(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "1048576")
        .build()
}
