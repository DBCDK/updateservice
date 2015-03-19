#!/bin/bash

BASEDIR=$(cd "$(dirname "$0")"; pwd)/..
GLASSFISH_HOME=$BASEDIR/home/glassfish4

# Deploy application
$GLASSFISH_HOME/bin/asadmin undeploy --port 13048 updateservice-app-1.0-SNAPSHOT
