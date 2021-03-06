package info.batey.kafka.unit.ssl;

import info.batey.kafka.unit.KafkaUnitWithSSL;
import info.batey.kafka.unit.config.CertStoreConfig;
import kafka.server.KafkaServer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG;
import static org.junit.Assert.assertEquals;

public class KafkaSSLIntegrationTest  {

    private KafkaUnitWithSSL kafkaUnitServer;
    private static final int ZOOKEEPER_PORT = 6000;
    private static final int BROKER_PORT = 6001;


    private void setUpDefaultKafkaServer() throws Exception {
        kafkaUnitServer = new KafkaUnitWithSSL(ZOOKEEPER_PORT, BROKER_PORT);
        startUpKafkaServer();
    }

    private void setUpKafkaServerWithCustomCertificates(CertStoreConfig certStoreConfig) {
        kafkaUnitServer = new KafkaUnitWithSSL(ZOOKEEPER_PORT, BROKER_PORT, certStoreConfig);
        startUpKafkaServer();
    }

    private void startUpKafkaServer() {
        kafkaUnitServer.setKafkaBrokerConfig("log.segment.bytes", "1024");
        kafkaUnitServer.startup();
    }

    @After
    public void shutdown() throws Exception {
        Field f = kafkaUnitServer.getClass().getSuperclass().getDeclaredField("broker");
        f.setAccessible(true);
        KafkaServer broker = (KafkaServer) f.get(kafkaUnitServer);
        assertEquals(1024, (int)broker.config().logSegmentBytes());
        kafkaUnitServer.shutdown();
    }

    @Test
    public void kafkaServerIsAvailable() throws Exception {
        setUpDefaultKafkaServer();

        assertKafkaServerIsAvailableWithSSL(kafkaUnitServer);
    }

    @Test
    public void canUseKafkaConnectToProduceWithSSL() throws Exception {
        setUpDefaultKafkaServer();

        final String topic = "KafkakConnectTestTopic";
        Properties props = getKafkaSSLConfigProperties();

        try (final Producer<Long, String> producer = new KafkaProducer<>(props)) {
            ProducerRecord<Long, String> record = new ProducerRecord<>(topic, 1L, "test");
            producer.send(record);
            final ConsumerRecords<String, String> expectedRecords = kafkaUnitServer.readMessages(topic, 1);
            assertEquals("test", getConsumerRecordsToList(expectedRecords).get(0));
        }
    }

    @Test
    public void canReadProducerRecords() throws Exception {
        setUpDefaultKafkaServer();

        //given
        String testTopic = "TestTopic";
        kafkaUnitServer.createTopic(testTopic);
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(testTopic, "key", "value");

        //when
        kafkaUnitServer.sendMessages(producerRecord);

        ConsumerRecords<String, String> receivedMessages = kafkaUnitServer.readMessages(testTopic, 1);
        ConsumerRecord<String, String> recievedMessage =null;
        if(receivedMessages.iterator().hasNext()){
          recievedMessage = receivedMessages.iterator().next();
        }

        assertEquals("Received message value is incorrect", "value", recievedMessage.value());
        assertEquals("Received message key is incorrect", "key", recievedMessage.key());
        assertEquals("Received message topic is incorrect", testTopic, recievedMessage.topic());
    }

    @Test
    public void kafkaShouldRunWithCustomCertificateConfig() throws Exception {
        setUpKafkaServerWithCustomCertificates(
            new CertStoreConfig(getCertStorePath(), "test1234", "test1234", "test1234", "test1234")
        );

        assertKafkaServerIsAvailableWithSSL(kafkaUnitServer);
    }

    private Properties getKafkaSSLConfigProperties() {
        String certStorePath = getCertStorePath();
        Properties props = new Properties();
        props.put("security.protocol", "SSL");
        props.put(SSL_TRUSTSTORE_LOCATION_CONFIG, certStorePath + "/client.truststore.jks");
        props.put(SSL_KEYSTORE_LOCATION_CONFIG, certStorePath + "/client.keystore.jks");
        props.put(SSL_TRUSTSTORE_PASSWORD_CONFIG, "test1234");
        props.put(SSL_KEYSTORE_PASSWORD_CONFIG, "test1234");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getCanonicalName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaUnitServer.getKafkaConnect());
        return props;
    }

    private void assertKafkaServerIsAvailableWithSSL(KafkaUnitWithSSL server) throws TimeoutException {
        //given
        String testTopic = "TestTopic";
        server.createTopic(testTopic);
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(testTopic, "key", "value");

        //when
        server.sendMessages(producerRecord);
        ConsumerRecords<String, String> messages = server.readMessages(testTopic, 1);
        List<String> expectedMessages = getConsumerRecordsToList(messages);
        //then
        assertEquals(Arrays.asList("value"),expectedMessages);
    }

    private String getCertStorePath() {
        final URL resource = this.getClass().getResource("/certStore");
        return resource.getPath();
    }

    private  List<String> getConsumerRecordsToList(ConsumerRecords<String, String> records)  {
        List<String> messages = new ArrayList<>();
        for(ConsumerRecord<String, String> record : records){
            messages.add(record.value());
        }
        return messages;
    };

}
