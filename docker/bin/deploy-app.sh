#!/usr/bin/env bash

echo "Copy $2 to container '$1'"
docker cp $2 $1:/home/gfish/.

echo "Deploy $2 in container '$1'"
docker exec $1 /usr/local/payara41/bin/asadmin --port 4848 --passwordfile=/home/gfish/passfile.txt deploy --force=true /home/gfish/$(basename $2)
