#!/bin/bash
#set -x

export IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))
cd $IDEA_ROOT/docker/deployments/dev

. $IDEA_ROOT/docker/bin/get_solr_port_nr.sh

export COMPOSE_PROJECT_NAME=${USER}
export HOST_IP=$(ip addr show | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' | head -1)

DOCKER_COMPOSE_CMD="$(command -v docker-compose && echo docker-compose || echo docker compose)"
${DOCKER_COMPOSE_CMD} down

