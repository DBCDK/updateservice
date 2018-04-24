#!/usr/bin/env bash

envsubst '${SUBST_IMAGE_NAME}' < docker/deployments/systemtests-payara/docker-compose.yml > docker/deployments/systemtests-payara/docker-compose.tmp
mv docker/deployments/systemtests-payara/docker-compose.tmp docker/deployments/systemtests-payara/docker-compose.yml