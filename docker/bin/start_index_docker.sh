#!/bin/bash
#set -x

export IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))
export HOST_IP=$(ip addr show | grep -A 99 '^2' | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' |egrep -v '^127.0.0.1' | head -1)

export DEV_OPENAGENCY_URL="http://openagency.addi.dk/test_2.34/"
export DEV_SOLR_ADDR=${HOST_IP}
export DEV_SOLR_PORT=`id -u ${USER}`5
export DEV_SOLR_PATH=solr/rawrepo
${IDEA_ROOT}/docker/bin/start_dev_docker.sh
