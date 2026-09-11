package shared.events

import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.koin.core.component.KoinComponent
import shared.utils.logger

interface EventProducer: KoinComponent {
    fun produceEvent(event: () -> CookncoEvent)
}

class KafkaEventProducer: EventProducer {
    private val json = Json { ignoreUnknownKeys = true }

    private val producer by lazy {
        KafkaProducer<String, String>(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to System.getenv("KAFKA_BOOTSTRAP_SERVERS")
                    ?: throw IllegalStateException("KAFKA_BOOTSTRAP_SERVERS not set"),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            )
        )
    }

    override fun produceEvent(event: () -> CookncoEvent) {
        val message = event()
        val data = json.encodeToString(message)
        val record = ProducerRecord(message.getTopic(), message.getKey(), data)
        // Publishing is not part of the caller's transaction: a broker outage drops the
        // event while the commit that caused it still stands. Logging is the floor, not a
        // fix — a transactional outbox table is what would actually close that gap.
        producer.send(record) { _, exception ->
            if (exception != null) {
                logger.error(exception) { "Failed to publish ${message.getTopic()} event" }
            }
        }
    }

}