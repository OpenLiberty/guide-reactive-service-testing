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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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

    private static final long POLL_TIMEOUT = 30 * 1000;
    
    @KafkaProducerConfig(keySerializer = StringSerializer.class,
                         valueSerializer = JsonbSerializer.class)
    public static KafkaProducer<String, Order> producer;

    @KafkaConsumerConfig(keyDeserializer = StringDeserializer.class,
                         valueDeserializer = OrderDeserializer.class,
                         groupId = "update-status",
                         topics = "statusTopic",
                         properties = ConsumerConfig.AUTO_OFFSET_RESET_CONFIG + "=earliest")
    public static KafkaConsumer<String, Order> consumer;

    private static io.openliberty.guides.models.Order order;

    @Test
    @org.junit.jupiter.api.Order(1)
    public void testInitFoodOrder() throws IOException, InterruptedException {
        Order order = new Order("0001", "1", Type.FOOD, "burger", Status.NEW);
        producer.send(new ProducerRecord<String, Order>("foodTopic", order));
        verify(Status.IN_PROGRESS);
    }
    
    @Test
    @org.junit.jupiter.api.Order(2)
    public void testFoodOrderReady() throws IOException, InterruptedException {
        Thread.sleep(10000);
        verify(Status.READY);
    }
    
    private void verify(Status expectedStatus) {
        int recordsProcessed = 0;
        long startTime = System.currentTimeMillis();
        long elapsedTime = 0;

        while (recordsProcessed == 0 && elapsedTime < POLL_TIMEOUT) {
            ConsumerRecords<String, Order> records = consumer.poll(Duration.ofMillis(3000));
            System.out.println("Polled " + records.count() + " records from Kafka:");
            for (ConsumerRecord<String, Order> record : records) {
                System.out.println(record.value());
                order = record.value();
                assertEquals("0001", order.getOrderId());
                assertEquals(expectedStatus, order.getStatus());
                recordsProcessed++;
            }
            consumer.commitAsync();
            if (recordsProcessed > 0)
                break;
            elapsedTime = System.currentTimeMillis() - startTime;
        }
        assertTrue(recordsProcessed > 0, "No records processed");
    }
}
