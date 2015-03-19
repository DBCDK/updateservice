#!/bin/bash

BASEDIR=$(cd "$(dirname "$0")"; pwd)/..
GLASSFISH_HOME=$BASEDIR/home/glassfish4

# Deploy application
$GLASSFISH_HOME/bin/asadmin deploy --port 13048 $BASEDIR/../../../updateservice-app/target/updateservice-app-1.0-SNAPSHOT.ear
