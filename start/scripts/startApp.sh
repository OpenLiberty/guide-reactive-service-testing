#!/bin/bash

KAFKA_MP_CONFIG=MP_MESSAGING_CONNECTOR_LIBERTY_KAFKA_BOOTSTRAP_SERVERS
KAFKA_SERVER=localhost:9092
NETWORK=reactive-app

export MP_MESSAGING_CONNECTOR_LIBERTY_KAFKA_BOOTSTRAP_SERVERS=$KAFKA_SERVER
echo MP_MESSAGING_CONNECTOR_LIBERTY_KAFKA_BOOTSTRAP_SERVERS=$MP_MESSAGING_CONNECTOR_LIBERTY_KAFKA_BOOTSTRAP_SERVERS

docker network create $NETWORK

docker run -d \
  -e ALLOW_ANONYMOUS_LOGIN=yes \
  --network=$NETWORK \
  --name=zookeeper \
  --rm \
  bitnami/zookeeper:3

docker run -d \
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e ALLOW_PLAINTEXT_LISTENER=yes \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://$KAFKA_SERVER \
  -p 9092:9092 \
  --hostname=kafka \
  --network=$NETWORK \
  --name=kafka \
  --rm \
  bitnami/kafka:2

mvn -pl kitchen liberty:start
mvn -pl bar liberty:start
mvn -pl servingWindow liberty:start
mvn -pl order liberty:start
mvn -pl status liberty:start
mvn -pl openLibertyCafe liberty:start