#!/bin/bash

WIREMOCK_HOST=localhost
WIREMOCK_PORT=12099

reqfiles=`ls requests/*.xml`
for req in $reqfiles ; do
    curl -X POST -d @$req http://$WIREMOCK_HOST:$WIREMOCK_PORT 
done
