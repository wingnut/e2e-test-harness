package example.kafka.scenario;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Test double: reacts to "order-persisted" events and emits "shipment-created" to the same topic.
 */
class FakeShipmentService extends AbstractFakeKafkaService {

    FakeShipmentService(String topic) {
        super(topic, "fake-shipment-service");
    }

    @Override
    protected String getTriggerPrefix() {
        return "order-persisted:";
    }

    @Override
    protected String buildResponsePayload(ConsumerRecord<String, String> record) {
        return "shipment-created:" + record.key();
    }
}
