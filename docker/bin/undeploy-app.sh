#!/usr/bin/env bash
#set -x

echo "Undeploy application $2 in container '$1'"
docker exec $1 /usr/local/payara41/bin/asadmin --port 4848 --passwordfile=/home/gfish/passfile.txt undeploy $(basename $2 .war)

echo "Remove $2.war in container '$1'"
docker exec $1 rm /home/gfish/$(basename $2)
