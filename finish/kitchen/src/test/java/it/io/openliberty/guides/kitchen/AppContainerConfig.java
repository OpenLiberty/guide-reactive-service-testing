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

import org.microshed.testing.SharedContainerConfiguration;
import org.microshed.testing.testcontainers.ApplicationContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;

// tag::AppContainerConfig[]
public class AppContainerConfig implements SharedContainerConfiguration {

    // Shared network between kafka and app containers
    private static Network network = Network.newNetwork();

    // Building the kafka container
    // tag::kafka[]
    // tag::container[]
    @Container
    // end::container[]
    public static KafkaContainer kafka = new KafkaContainer()
                    .withNetwork(network);
    // end::kafka[]

    // The kitchen service container
    // tag::kitchen[]
    // tag::container[]
    @Container
    // end::container[]
    public static ApplicationContainer app = new ApplicationContainer()
                    .withAppContextRoot("/")
                    .withReadinessPath("/health/ready")
                    .withExposedPorts(9083)
                    .withNetwork(network)
                    // tag::dependsOn[]
                    .dependsOn(kafka);
                    // end::dependsOn[]
    // end::kitchen[]
}
// end::AppContainerConfig[]
