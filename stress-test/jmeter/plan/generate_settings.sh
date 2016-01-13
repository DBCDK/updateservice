#!/bin/bash

set -e

env

cat << EOF > ${APP_USER_HOME}/plan/settings.properties
update.host=${UPDATE_PORT_8080_TCP_ADDR}
update.port=${UPDATE_PORT_8080_TCP_PORT}
EOF
