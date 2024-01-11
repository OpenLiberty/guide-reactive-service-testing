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
package it.io.openliberty.guides.inventory;

import java.util.List;
import java.net.Socket;
import java.time.Duration;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.Properties;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.client.ClientBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
// tag::KafkaProducer[]
import org.apache.kafka.clients.producer.KafkaProducer;
// end::KafkaProducer[]
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import io.openliberty.guides.models.SystemLoad;
import io.openliberty.guides.models.SystemLoad.SystemLoadSerializer;


@Testcontainers
// tag::InventoryServiceIT[]
public class InventoryServiceIT {

    private static Logger logger = LoggerFactory.getLogger(InventoryServiceIT.class);

    public static InventoryResourceClient client;

    private static Network network = Network.newNetwork();
    // tag::KafkaProducer2[]
    public static KafkaProducer<String, SystemLoad> producer;
    // end::KafkaProducer2[]
    private static ImageFromDockerfile inventoryImage
        = new ImageFromDockerfile("inventory:1.0-SNAPSHOT")
            .withDockerfile(Paths.get("./Dockerfile"));

    private static KafkaContainer kafkaContainer = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:latest"))
            .withListener(() -> "kafka:19092")
            .withNetwork(network);

    private static GenericContainer<?> inventoryContainer =
        new GenericContainer(inventoryImage)
            .withNetwork(network)
            .withExposedPorts(9085)
            .waitingFor(Wait.forHttp("/health/ready").forPort(9085))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .dependsOn(kafkaContainer);

    // tag::RESTClient[]
    private static InventoryResourceClient createRestClient(String urlPath) {
        ClientBuilder builder = ResteasyClientBuilder.newBuilder();
        // tag::ResteasyClient[]
        ResteasyClient client = (ResteasyClient) builder.build();
        // end::ResteasyClient[]
        ResteasyWebTarget target = client.target(UriBuilder.fromPath(urlPath));
        return target.proxy(InventoryResourceClient.class);
    }
    // end::RESTClient[]

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

        String urlPath;
        if (isServiceRunning("localhost", 9085)) {
            System.out.println("Testing with mvn liberty:devc");
            // tag::urlPathSetup1[]
            urlPath = "http://localhost:9085";
            // end::urlPathSetup1[]
        } else {
            kafkaContainer.start();
            inventoryContainer.withNetwork(network);
            inventoryContainer.withEnv(
            "mp.messaging.connector.liberty-kafka.bootstrap.servers", "kafka:19092");
            System.out.println("Testing with mvn verify");
            inventoryContainer.start();
            // tag::urlPathSetup2[]
            urlPath = "http://"
                + inventoryContainer.getHost()
                + ":" + inventoryContainer.getFirstMappedPort();
            // end::urlPathSetup2[]
        }

        System.out.println("Creating REST client with: " + urlPath);
        client = createRestClient(urlPath);
    }

    @BeforeEach
    public void setUp() {
        // tag::KafkaProducerProps[]
        Properties producerProps = new Properties();
        if (isServiceRunning("localhost", 9085)) {
            // tag::BootstrapServerConfig[]
            producerProps.put(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9094");
            // end::BootstrapServerConfig[]
        } else {
            // tag::BootstrapServerConfig2[]
            producerProps.put(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
            // end::BootstrapServerConfig2[]
        }
        
        producerProps.put(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        producerProps.put(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                SystemLoadSerializer.class.getName());

        producer = new KafkaProducer<String, SystemLoad>(producerProps);
        // end::KafkaProducerProps[]
    }

    @AfterAll
    public static void stopContainers() {
        client.resetSystems();
        inventoryContainer.stop();
        kafkaContainer.stop();
        if (network != null) {
            network.close();
        }
    }

    @AfterEach
    public void tearDown() {
        producer.close();
    }

    // tag::testCpuUsage[]
    @Test
    public void testCpuUsage() throws InterruptedException {
        SystemLoad sl = new SystemLoad("localhost", 1.1);
        // tag::systemLoadTopic[]
        producer.send(new ProducerRecord<String, SystemLoad>("system.load", sl));
        // end::systemLoadTopic[]
        Thread.sleep(5000);
        Response response = client.getSystems();
        Assertions.assertEquals(200, response.getStatus(),
                "Response should be 200");
        List<Properties> systems =
                response.readEntity(new GenericType<List<Properties>>() { });
        // tag::assert[]
        Assertions.assertEquals(systems.size(), 1);
        // end::assert[]
        for (Properties system : systems) {
            // tag::assert2[]
            Assertions.assertEquals(sl.hostname, system.get("hostname"),
                    "Hostname doesn't match!");
            // end::assert2[]
            BigDecimal systemLoad = (BigDecimal) system.get("systemLoad");
            // tag::assert3[]
            Assertions.assertEquals(sl.loadAverage, systemLoad.doubleValue(),
                    "CPU load doesn't match!");
            // end::assert3[]
        }
    }
    // end::testCpuUsage[]
}
// end::InventoryServiceIT[]
