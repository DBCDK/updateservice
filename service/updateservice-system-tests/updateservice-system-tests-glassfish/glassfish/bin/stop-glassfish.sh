#!/bin/bash

BASEDIR=$(cd "$(dirname "$0")"; pwd)/..
GLASSFISH_HOME=$BASEDIR/home/glassfish4

$GLASSFISH_HOME/bin/asadmin stop-domain
