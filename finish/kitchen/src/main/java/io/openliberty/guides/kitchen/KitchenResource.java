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
package io.openliberty.guides.kitchen;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Path;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import io.openliberty.guides.models.Order;
import io.openliberty.guides.models.Status;

@ApplicationScoped
@Path("/foodMessaging")
public class KitchenResource {

    private static Logger logger = Logger.getLogger(KitchenResource.class.getName());

    private Executor executor = Executors.newSingleThreadExecutor();
    private BlockingQueue<Order> inProgress = new LinkedBlockingQueue<>();
    private Random random = new Random();

    // tag::Incoming[]
    @Incoming("foodOrderConsume")
    // end::Incoming[]
    // tag::Outgoing[]
    @Outgoing("foodOrderPublishStatus")
    // end::Outgoing[]
    // tag::initFoodOrder[]
    public Order initFoodOrder(Order newOrder) {
        logger.info("Order " + newOrder.getOrderId() + " received with a status of NEW");
        logger.info(newOrder.toString());
        return prepareOrder(newOrder);
    }
    // end::initFoodOrder[]

    @Outgoing("foodOrderPublishStatus")
    public Order sendReadyOrder() {
        try {
            Order order = inProgress.take();
            prepare(5);
            order.setStatus(Status.READY);
            logger.info("Order " + order.getOrderId() + " is READY");
            logger.info(order.toString());
            return order;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Order prepareOrder(Order order) {
        prepare(5);
        Order inProgressOrder = order.setStatus(Status.IN_PROGRESS);
        logger.info("Order " + order.getOrderId() + " is IN PROGRESS");
        inProgress.add(inProgressOrder);
        return inProgressOrder;
    }

    private void prepare(int sleepTime) {
        try {
            Thread.sleep((random.nextInt(5)+sleepTime) * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
