#!/bin/bash
#set -x

# If this is set different from N then also change
# image: docker-i.dbc.dk/update-payara:latest
# to
# image: docker-i.dbc.dk/update-payara:mib (or whomever you are)
# in docker-compos.yml
# also go to updateservice/docker/update-payara and make :
# docker build -t docker-i.dbc.dk/update-payara:mib .
USE_LOCAL_PAYARA="Y"

SOLR_PORT_NR=${SOLR_PORT_NR:-WHAT}     # silencing annoying intellij quibble
SOLR_BASIS_PORT_NR=${SOLR_BASIS_PORT_NR:-WHAT}
export IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))

RAWREPO_VERSION=1.13-snapshot
RAWREPO_DIT_TAG=DIT-5016
HOLDINGS_ITEMS_VERSION=1.1.4-snapshot

cd ${IDEA_ROOT}/docker

res=$?
if [ ${res} -ne 0 ]
then
    echo "Could not create ${IDEA_ROOT}/docker/logs/update/app ${IDEA_ROOT}/docker/logs/update/server"
    exit 1
fi

res=$?
if [ ${res} -ne 0 ]
then
    echo "Could not set o+rw for ${IDEA_ROOT}/logs and subdirectories"
    exit 1
fi

cd ${IDEA_ROOT}/docker/deployments/dev

. ${IDEA_ROOT}/docker/bin/get_solr_port_nr.sh

if [ ! -d $HOME/.ocb-tools ]
then
    mkdir $HOME/.ocb-tools
    res=$?
    if [ ${res} -ne 0 ]
    then
        echo "Could not create directory $HOME/.ocb-tools"
        echo "the directory are necessary so we stop now"
        exit 1
    fi
fi

USER=${USER:-"unknown"}
export COMPOSE_PROJECT_NAME=${USER}

#Find the correct outbound ip-address regardless of host configuration
if [ "$(uname)" == "Darwin" ]
then
    export HOST_IP=$(ip addr show | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' | egrep -v '^127.0.0.1' | head -1)
else
    export HOST_IP=$( ip -o addr show | grep "inet " | cut -d: -f2- | cut -c2- | egrep -v "^docker|^br" | grep "$(ip route list | grep default | cut -d' ' -f5) " | cut -d' ' -f6 | cut -d/ -f1)
fi

docker-compose down
docker-compose ps
echo "docker ps : $?"

docker rmi -f docker-io.dbc.dk/rawrepo-postgres-${RAWREPO_VERSION}:${USER}
docker rmi -f docker-io.dbc.dk/holdings-items-postgres-${HOLDINGS_ITEMS_VERSION}:${USER}
docker rmi -f docker-i.dbc.dk/update-postgres:${USER}
if [ "$USE_LOCAL_PAYARA" = "N" ]
then
    docker rmi -f docker-i.dbc.dk/update-payara:${USER}
fi
docker-compose pull
docker-compose up -d rawrepoDb
docker-compose up -d updateserviceDb
docker-compose up -d holdingsitemsDb
docker-compose up -d fakeSmtp
sleep 3
docker tag docker-io.dbc.dk/rawrepo-postgres-${RAWREPO_VERSION}:${RAWREPO_DIT_TAG} docker-io.dbc.dk/rawrepo-postgres-${RAWREPO_VERSION}:${USER}
docker rmi docker-io.dbc.dk/rawrepo-postgres-${RAWREPO_VERSION}:${RAWREPO_DIT_TAG}
docker tag docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_ITEMS_VERSION}:latest docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_ITEMS_VERSION}:${USER}
docker rmi docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_ITEMS_VERSION}:latest
docker tag docker-i.dbc.dk/update-postgres:latest docker-i.dbc.dk/update-postgres:${USER}
docker rmi docker-i.dbc.dk/update-postgres:latest

RAWREPO_IMAGE=`docker-compose ps -q rawrepoDb`
export RAWREPO_PORT=`docker inspect --format='{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' ${RAWREPO_IMAGE} `
echo -e "RAWREPO_PORT is $RAWREPO_PORT\n"

HOLDINGSITEMSDB_IMAGE=`docker-compose ps -q holdingsitemsDb`
export HOLDINGSITEMSDB_PORT=`docker inspect --format='{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' ${HOLDINGSITEMSDB_IMAGE} `
echo -e "HOLDINGSITEMSDB_PORT is $HOLDINGSITEMSDB_PORT\n"

UPDATESERVICEDB_IMAGE=`docker-compose ps -q updateserviceDb`
export UPDATESERVICEDB_PORT=`docker inspect --format='{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' ${UPDATESERVICEDB_IMAGE} `
echo -e "UPDATESERVICEDB_PORT is $UPDATESERVICEDB_PORT\n"

echo "rawrepo.jdbc.driver = org.postgresql.Driver" > ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.jdbc.conn.url = jdbc:postgresql://${HOST_IP}:${RAWREPO_PORT}/rawrepo" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.jdbc.conn.user = rawrepo" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.jdbc.conn.passwd = thePassword" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.db.url= rawrepo:thePassword@${HOST_IP}:${RAWREPO_PORT}/rawrepo" >> ${HOME}/.ocb-tools/testrun.properties

echo "holdings.jdbc.driver = org.postgresql.Driver" >> ${HOME}/.ocb-tools/testrun.properties
echo "holdings.jdbc.conn.url = jdbc:postgresql://${HOST_IP}:${HOLDINGSITEMSDB_PORT}/holdingsitems" >> ${HOME}/.ocb-tools/testrun.properties
echo "holdings.jdbc.conn.user = holdingsitems" >> ${HOME}/.ocb-tools/testrun.properties
echo "holdings.jdbc.conn.passwd = thePassword" >> ${HOME}/.ocb-tools/testrun.properties
echo "holdings.db.url= holdingsitems:thePassword@${HOST_IP}:${HOLDINGSITEMSDB_PORT}/holdingsitems" >> ${HOME}/.ocb-tools/testrun.properties

echo "updateservice.db.url= updateservice:thePassword@${HOST_IP}:${UPDATESERVICEDB_PORT}/updateservice" >> ${HOME}/.ocb-tools/testrun.properties

echo "solr.port = ${SOLR_PORT_NR}" >> ${HOME}/.ocb-tools/testrun.properties

echo "request.headers.x.forwarded.for = ${HOST_IP}" >> ${HOME}/.ocb-tools/testrun.properties

echo "rawrepo.provider.name.dbc = dataio-update" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.provider.name.fbs = opencataloging-update" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.provider.name.ph = fbs-ph-update" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.provider.name.ph.holdings = dataio-ph-holding-update" >> ${HOME}/.ocb-tools/testrun.properties

echo "updateservice.url = dummy" >> ${HOME}/.ocb-tools/testrun.properties
echo "buildservice.url = dummy" >> ${HOME}/.ocb-tools/testrun.properties