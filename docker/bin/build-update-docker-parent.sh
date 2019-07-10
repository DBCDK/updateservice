#!/usr/bin/env bash

export IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))

cd ${IDEA_ROOT}/docker/update-payara

docker build -t docker-i.dbc.dk/update-payara:latest .

cd -