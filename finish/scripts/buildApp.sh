#!/bin/bash

echo Building application

mvn -pl models install
mvn -pl kitchen package liberty:create liberty:install-feature liberty:deploy
mvn -pl bar package liberty:create liberty:install-feature liberty:deploy
mvn -pl servingWindow package liberty:create liberty:install-feature liberty:deploy
mvn -pl order package liberty:create liberty:install-feature liberty:deploy
mvn -pl status package liberty:create liberty:install-feature liberty:deploy
mvn -pl openLibertyCafe package liberty:create liberty:install-feature liberty:deploy

echo Application building completed