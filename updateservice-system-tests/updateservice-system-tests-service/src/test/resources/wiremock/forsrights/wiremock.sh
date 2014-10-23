#!/bin/bash

WIREMOCK_JAR=../wiremock-1.50-standalone.jar

FORSRIGHTS_HOST=http://forsrights.addi.dk/1.2

WIREMOCK_PORT=12099

rm -rf __files
rm -rf mappings

java -jar $WIREMOCK_JAR --port $WIREMOCK_PORT --proxy-all="$FORSRIGHTS_HOST" --record-mappings --verbose
