// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020, 2023 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
// end::copyright[]
package it.io.openliberty.guides.system;

import java.net.Socket;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
// tag::KafkaConsumer[]
import org.apache.kafka.clients.consumer.KafkaConsumer;
// end::KafkaConsumer[]
import org.apache.kafka.common.serialization.StringDeserializer;

import io.openliberty.guides.models.SystemLoad;
import io.openliberty.guides.models.SystemLoad.SystemLoadDeserializer;

@Testcontainers
public class SystemServiceIT {

    private static Logger logger = LoggerFactory.getLogger(SystemServiceIT.class);

    private static Network network = Network.newNetwork();
    
    // tag::KafkaConsumer2[]
    public static KafkaConsumer<String, SystemLoad> consumer;
    // end::KafkaConsumer2[]

    // tag::buildSystemImage[]
    private static ImageFromDockerfile systemImage
        = new ImageFromDockerfile("system:1.0-SNAPSHOT")
              .withDockerfile(Paths.get("./Dockerfile"));
    // end::buildSystemImage[]

    // tag::kafkaContainer[]
    private static KafkaContainer kafkaContainer = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:latest"))
            // tag::withListener[]
            .withListener(() -> "kafka:19092")
            // end::withListener[]
            // tag::network1[]
            .withNetwork(network);
            // end::network1[]
    // end::kafkaContainer[]

    // tag::systemContainer[]
    private static GenericContainer<?> systemContainer =
        new GenericContainer(systemImage)
            // tag::network2[]
            .withNetwork(network)
            // end::network2[]
            .withExposedPorts(9083)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9083))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            // tag::dependsOn[]
            .dependsOn(kafkaContainer);
            // end::dependsOn[]
    // end::systemContainer[]

    // tag::isServiceRunning[]
    private static boolean isServiceRunning(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    // end::isServiceRunning[]

    @BeforeAll
    public static void startContainers() {
        if (isServiceRunning("localhost", 9083)) {
            System.out.println("Testing with mvn liberty:devc");
        } else {
            kafkaContainer.start();
            // tag::bootstrapServerSetup[]
            systemContainer.withEnv(
            "mp.messaging.connector.liberty-kafka.bootstrap.servers", "kafka:19092");
            // end::bootstrapServerSetup[]
            systemContainer.start();
            System.out.println("Testing with mvn verify");
        }
    }

    @BeforeEach
    public void setUp() {
        // tag::KafkaConsumer2[]
        // tag::KafkaConsumerProps[]
        Properties consumerProps = new Properties();
        if (isServiceRunning("localhost", 9083)) {
            // tag::BootstrapSetting1[]
            consumerProps.put(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9094");
            // end::BootstrapSetting1[]
        } else {
            consumerProps.put(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            // tag::BootstrapSetting2[]
                kafkaContainer.getBootstrapServers());
            // end::BootstrapSetting2[]
        }
        consumerProps.put(
            ConsumerConfig.GROUP_ID_CONFIG,
                "system-load-status");
        consumerProps.put(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        // tag::valueDeserializer[]
        consumerProps.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                SystemLoadDeserializer.class.getName());
        // end::valueDeserializer[]
        consumerProps.put(
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        // end::KafkaConsumerProps[]
        consumer = new KafkaConsumer<String, SystemLoad>(consumerProps);
        // tag::systemLoadTopic[]
        consumer.subscribe(Collections.singletonList("system.load"));
        // end::systemLoadTopic[]
        // end::KafkaConsumer2[]
    }


    @AfterAll
    public static void stopContainers() {
        systemContainer.stop();
        kafkaContainer.stop();
        if (network != null) {
            network.close();
        }
    }

    @AfterEach
    public void tearDown() {
        consumer.close();
    }

    // tag::testCpuStatus[]
    @Test
    public void testCpuStatus() {
        // tag::poll[]
        ConsumerRecords<String, SystemLoad> records =
                consumer.poll(Duration.ofMillis(30 * 1000));
        // end::poll[]
        System.out.println("Polled " + records.count() + " records from Kafka:");

        for (ConsumerRecord<String, SystemLoad> record : records) {
            SystemLoad sl = record.value();
            System.out.println(sl);
            // tag::assert[]
            assertNotNull(sl.hostname);
            assertNotNull(sl.loadAverage);
            // end::assert[]
        }
        consumer.commitAsync();
    }
    // end::testCpuStatus[]
}
