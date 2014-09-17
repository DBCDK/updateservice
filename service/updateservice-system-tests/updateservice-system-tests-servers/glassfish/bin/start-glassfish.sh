#!/bin/bash

BASEDIR=$(cd "$(dirname "$0")"; pwd)/..
GLASSFISH_HOME=$BASEDIR/home/glassfish4

# Cleanup home dir.
./clean-glassfish.sh

# Unpack Glassfish distribution
unzip $BASEDIR/glassfish-4.0.zip -d $BASEDIR/home

# Setup preinstalled domain "domain1"
pwd
cd ../..
mvn verify
cd -

# Start domain "domain1"
$GLASSFISH_HOME/bin/asadmin start-domain
