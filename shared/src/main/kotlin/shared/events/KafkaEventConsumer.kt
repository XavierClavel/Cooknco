package shared.events

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import shared.utils.logger
import java.time.Duration

/**
 * At-least-once: an offset is committed only once its record has been handled without
 * throwing, so a failure is retried rather than skipped.
 *
 * The alternative, committing the batch whatever happened, makes every failure permanent
 * and silent — a mail nobody can prove was never sent. Handlers pay for that by having to
 * tolerate redelivery, and by having to throw on failure rather than swallow it.
 */
class KafkaEventConsumer(
    private val groupId: String,
    private val topics: List<String>,
    private val handle: (CookncoEvent) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val consumer = KafkaConsumer<String, String>(
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to System.getenv("KAFKA_BOOTSTRAP_SERVERS"),
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringDeserializer",
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringDeserializer",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            // Offsets are committed by hand, one record at a time, after the handler returns.
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
            // A handler blocks for as long as an SMTP round trip, so a batch has to stay
            // small enough to finish inside max.poll.interval.ms. Past it the broker drops
            // this member mid-send and the group rebalances, repeatedly.
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "16",
        )
    )

    fun start() {
        consumer.subscribe(topics)
        while (true) {
            val records = consumer.poll(POLL_TIMEOUT)
            // First offset not yet handled, per partition: where to rewind to on failure.
            // poll() has already moved the position past the whole batch, so every partition
            // in it has to be rewound, not only the one whose record threw.
            val unhandled = records.partitions()
                .associateWithTo(mutableMapOf()) { records.records(it).first().offset() }

            for (record in records) {
                val partition = TopicPartition(record.topic(), record.partition())
                try {
                    handle(json.decodeFromString<CookncoEvent>(record.value()))
                } catch (e: SerializationException) {
                    // No retry fixes a record this build cannot decode, and leaving it in
                    // place would wedge its partition for good. Drop it, loudly.
                    logger.error(e) { "Discarding undecodable record at $partition@${record.offset()}" }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to handle $partition@${record.offset()}, retrying" }
                    unhandled.forEach { (tp, offset) -> consumer.seek(tp, offset) }
                    Thread.sleep(RETRY_BACKOFF.toMillis())
                    break
                }
                unhandled[partition] = record.offset() + 1
                consumer.commitSync(mapOf(partition to OffsetAndMetadata(record.offset() + 1)))
            }
        }
    }

    private companion object {
        val POLL_TIMEOUT: Duration = Duration.ofSeconds(1)
        val RETRY_BACKOFF: Duration = Duration.ofSeconds(5)
    }
}
