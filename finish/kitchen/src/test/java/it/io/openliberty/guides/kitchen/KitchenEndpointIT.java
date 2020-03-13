// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial implementation
 *******************************************************************************/
// end::copyright[]
package it.io.openliberty.guides.kitchen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.microshed.testing.SharedContainerConfig;
import org.microshed.testing.jupiter.MicroShedTest;
import org.microshed.testing.kafka.KafkaConsumerConfig;
import org.microshed.testing.kafka.KafkaProducerConfig;

import io.openliberty.guides.models.*;
import io.openliberty.guides.models.Order.JsonbSerializer;
import io.openliberty.guides.models.Order.OrderDeserializer;

@MicroShedTest
@SharedContainerConfig(AppContainerConfig.class)
public class KitchenEndpointIT {

    private static final long POLL_TIMEOUT = 10 * 1000;

    // tag::KafkaProducerConfig[]
    @KafkaProducerConfig(valueSerializer = JsonbSerializer.class)
    // end::KafkaProducerConfig[]
    public static KafkaProducer<String, Order> producer;

    // tag::KafkaConsumerConfig[]
    @KafkaConsumerConfig(valueDeserializer = OrderDeserializer.class,
                         groupId = "update-status",
                         // tag::statusTopic[]
                         topics = "statusTopic",
                         // end::statusTopic[]
                         properties = ConsumerConfig.AUTO_OFFSET_RESET_CONFIG + "=earliest")
    // end::KafkaConsumerConfig[]
    public static KafkaConsumer<String, Order> consumer;

    @Test
    @org.junit.jupiter.api.Order(1)
    public void testInProgress() {
        Order newOrder = new Order("0001", "1", Type.FOOD, "burger", Status.NEW);
        // tag::foodTopic[]
        producer.send(new ProducerRecord<String, Order>("foodTopic", newOrder));
        // end::foodTopic[]
        verify(Status.IN_PROGRESS);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    public void testReady(){
        verify(Status.READY);
    }

    private void verify(Status expectedStatus) {
        System.out.println("Waiting to receive " + expectedStatus +
                " order from Kafka");
        ConsumerRecords<String, Order> records = consumer.poll(Duration.ofSeconds(30));
        System.out.println("Polled " + records.count() + " records from Kafka:");

        assertEquals(1, records.count(), "Expected to poll exactly 1 order from Kafka");
        for (ConsumerRecord<String, Order> record : records) {
            System.out.println(record.value());
            Order receivedOrder = record.value();
            assertEquals("0001", receivedOrder.getOrderId(), "Order ID did not match expected");
            assertEquals(expectedStatus, receivedOrder.getStatus(), "Status did not match expected");
        }
        consumer.commitAsync();
    }
}
