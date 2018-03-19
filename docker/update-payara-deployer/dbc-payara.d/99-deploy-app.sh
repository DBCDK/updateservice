#!/bin/bash
echo "==> Deploy application"

set -e 

cat_syslog () {
    echo "Contents of Server.log:"
    cat $PAYARA_HOME/glassfish/domains/domain1/logs/server.log
}

trap cat_syslog ERR

$PAYARA_HOME/bin/asadmin --port 4848 --passwordfile=$PAYARA_USER_HOME/passfile.txt deploy /data/updateservice-2.0-SNAPSHOT.war
