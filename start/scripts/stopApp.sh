#!/bin/bash

docker stop kafka zookeeper &
mvn -pl kitchen liberty:stop &
mvn -pl bar liberty:stop &
mvn -pl order liberty:stop &
mvn -pl status liberty:stop &
mvn -pl servingWindow liberty:stop  &
mvn -pl openLibertyCafe liberty:stop &
wait

docker network rm reactive-app