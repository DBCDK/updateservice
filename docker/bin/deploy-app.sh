#!/usr/bin/env bash

echo "Copy $2 to container '$1'"
docker container cp $2 $(docker container ls -f name=$1 -q):/home/gfish/.

echo "Deploy $2 in container '$1'"
docker container exec $(docker container ls -f name=$1 -q) /usr/local/payara41/bin/asadmin --port 4848 --passwordfile=/home/gfish/passfile.txt deploy --force=true /home/gfish/$(basename $2)
