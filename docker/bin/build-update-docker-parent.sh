#!/usr/bin/env bash

export IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))

cd ${IDEA_ROOT}/docker/update-payara

docker build -t docker-metascrum.artifacts.dbccloud.dk/update-payara:latest .

cd -
