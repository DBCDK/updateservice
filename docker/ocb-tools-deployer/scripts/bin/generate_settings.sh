#!/bin/bash

set -e

mkdir -p ${OCB_USER_HOME}/.ocb-tools
cat << EOF > ${OCB_USER_HOME}/.ocb-tools/settings.properties
updateservice.url = http://${UPDATE_SERVICE_PORT_8080_TCP_ADDR}:${UPDATE_SERVICE_PORT_8080_TCP_PORT}
buildservice.url = http://${UPDATE_SERVICE_PORT_8080_TCP_ADDR}:${UPDATE_SERVICE_PORT_8080_TCP_PORT}

rawrepo.jdbc.driver = org.postgresql.Driver
rawrepo.jdbc.conn.url = jdbc:postgresql://${RAWREPO_PORT_5432_TCP_ADDR}:${RAWREPO_PORT_5432_TCP_PORT}/${RAWREPO_DBNAME}
rawrepo.jdbc.conn.user = ${RAWREPO_USER}
rawrepo.jdbc.conn.passwd = ${RAWREPO_PASSWORD}

holdings.port = 8081

solr.port = 8080

request.headers.x.forwarded.for = ${REQUEST_IP_ADDR}

rawrepo.provider.name.dbc = dataio-update
rawrepo.provider.name.fbs = opencataloging-update
rawrepo.provider.name.ph = fbs-ph-update
rawrepo.provider.name.ph.holdings = dataio-ph-holding-update
EOF
