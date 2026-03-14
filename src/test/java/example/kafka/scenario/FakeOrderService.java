package example.kafka.scenario;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Test double: reacts to "order-placed" events and emits "order-persisted" to the same topic.
 */
class FakeOrderService extends AbstractFakeKafkaService {

    FakeOrderService(String topic) {
        super(topic, "fake-order-service");
    }

    @Override
    protected String getTriggerPrefix() {
        return "order-placed:";
    }

    @Override
    protected String buildResponsePayload(ConsumerRecord<String, String> record) {
        return "order-persisted:" + record.key();
    }
}
