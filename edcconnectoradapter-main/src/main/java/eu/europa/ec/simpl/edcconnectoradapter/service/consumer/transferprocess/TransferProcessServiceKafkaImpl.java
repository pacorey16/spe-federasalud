package eu.europa.ec.simpl.edcconnectoradapter.service.consumer.transferprocess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.transfer.TransferProcess;
import eu.europa.ec.simpl.data1.common.adapter.connector.model.transfer.TransferRequest;
import eu.europa.ec.simpl.data1.common.exception.KafkaNotRetryableException;
import eu.europa.ec.simpl.data1.common.exception.KafkaRetryableException;
import eu.europa.ec.simpl.edcconnectoradapter.client.edcconnector.EDCConnectorTransferClient;
import eu.europa.ec.simpl.edcconnectoradapter.model.edc.common.response.EdcAcknowledgementId;
import eu.europa.ec.simpl.edcconnectoradapter.model.kafka.TransferDeprovisioning;
import eu.europa.ec.simpl.edcconnectoradapter.util.ObjectMapperUtil;
import feign.FeignException;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Profile({"consumer", "test"})
@Log4j2
@Service
public class TransferProcessServiceKafkaImpl extends TransferProcessServiceImpl {

    @Value(value = "${kafka.transfer-status.topic}")
    private String transferStatusTopic;

    @Value(value = "${kafka.transfer-deprovisioning.topic}")
    private String transferDeprovisioningTopic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper;

    public TransferProcessServiceKafkaImpl(
            EDCConnectorTransferClient edcConnectorTransferClient,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        super(edcConnectorTransferClient);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @SneakyThrows
    @Override
    protected void handleDeprovisioning(TransferRequest transferRequest, EdcAcknowledgementId acknowledgementId) {
        String transferProcessId = acknowledgementId.getId();
        if (isDeprovisioningEnabled(transferRequest)) {
            TransferDeprovisioning transferDeprovisioning = new TransferDeprovisioning(acknowledgementId.getId());
            produceKafkaRecord(transferStatusTopic, transferDeprovisioning);
        } else {
            log.debug(
                    "handleDeprovisioning(): dataDestination deprovisioning not enabled, skipping deprovisioning for transferProcessId {}",
                    transferProcessId);
        }
    }

    @KafkaListener(
            topics = "${kafka.transfer-status.topic}",
            containerFactory = "transferStatusListenerContainerFactory")
    public void kafkaTranferStatusConsumer(ConsumerRecord<String, String> consumerRecord)
            throws JsonProcessingException {

        String payload = consumerRecord.value();
        List<String> headers = ObjectMapperUtil.headersToList(consumerRecord.headers());
        log.debug("kafkaTranferStatusConsumer() for payload '{}' with headers '{}'", payload, headers);

        TransferDeprovisioning transferDeprovisioning =
                ObjectMapperUtil.getObjectFromPayload(payload, TransferDeprovisioning.class, objectMapper);

        String transferProcessId = transferDeprovisioning.getTransferProcessId();
        TransferProcess transferProcess = getTransferProcess(transferProcessId);
        String state = transferProcess.getState();

        if (COMPLETED_STATUS.equals(state)) {
            log.debug("kafkaTranferStatusConsumer(): transfer process completed for {}", consumerRecord);
            produceKafkaRecord(transferDeprovisioningTopic, transferDeprovisioning);
        } else {
            log.debug(
                    "kafkaTranferStatusConsumer(): transfer process not completed yet (state: {}), retrying {}",
                    state,
                    consumerRecord);
            throw new KafkaRetryableException("Transfer process with id " + transferProcessId
                    + " not completed yet (state: " + state + "), retrying...");
        }
    }

    @KafkaListener(
            topics = "${kafka.transfer-deprovisioning.topic}",
            containerFactory = "transferDeprovisioningListenerContainerFactory")
    public void kafkaTranferDeprovisioningConsumer(ConsumerRecord<String, String> consumerRecord)
            throws JsonProcessingException {

        String payload = consumerRecord.value();
        List<String> headers = ObjectMapperUtil.headersToList(consumerRecord.headers());
        log.debug("kafkaTranferDeprovisioningConsumer() for payload '{}' with headers '{}'", payload, headers);

        TransferDeprovisioning transferDeprovisioning =
                ObjectMapperUtil.getObjectFromPayload(payload, TransferDeprovisioning.class, objectMapper);

        String transferProcessId = transferDeprovisioning.getTransferProcessId();
        Map<String, String> headersMap = createHttpHeadersMap();
        try {
            log.debug(
                    "kafkaTranferDeprovisioningConsumer(): invoking edcConnectorClientApi.deprovisioningTransferProcess() for transferProcessId {}",
                    transferProcessId);
            edcConnectorTransferClient.deprovisioningTransferProcess(
                    edcConsumerManagementUrl, headersMap, transferProcessId);
            log.debug(
                    "kafkaTranferDeprovisioningConsumer() completed successfully for transferProcessId {}",
                    transferProcessId);
        } catch (FeignException.NotFound | FeignException.Conflict e) {
            log.warn(
                    "kafkaTranferDeprovisioningConsumer(): deprovisioning ignored for transferProcessId {}: {}",
                    transferProcessId,
                    e);
            throw new KafkaNotRetryableException("deprovisioning ignored cause response code " + e.status(), e);
        } catch (Exception e) {
            log.error(
                    "kafkaTranferDeprovisioningConsumer() failed deprovisioning for transferProcessId {}: {}",
                    transferProcessId,
                    e);
            throw new KafkaRetryableException("deprovisioning failed", e);
        }
    }

    private void produceKafkaRecord(String topic, Object item) throws JsonProcessingException {
        ProducerRecord<String, Object> producerRecord =
                new ProducerRecord<>(topic, objectMapper.writeValueAsString(item));
        log.debug("produceKafkaRecord(): invoking kafkaTemplate.send() for {}", producerRecord);
        kafkaTemplate.send(producerRecord);
    }
}
