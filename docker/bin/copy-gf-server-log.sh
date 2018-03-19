#!/usr/bin/env bash

docker cp $1:/usr/local/payara41/glassfish/domains/domain1/logs/server.log $2
