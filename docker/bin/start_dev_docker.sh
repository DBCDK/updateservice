#!/usr/bin/env bash
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

RAWREPO_VERSION=ms3633-snapshot
RAWREPO_DIT_TAG=MS-3633_improve_enqueue_performance-2
HOLDINGS_ITEMS_VERSION=1.1.4-snapshot
OPENCAT_BUSINESS_SERVICE_TAG=latest
RAWREPO_RECORD_SERVICE_TAG=DIT-271

cd ${IDEA_ROOT}/docker

docker build -t docker-i.dbc.dk/update-payara:latest update-payara

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
elif [ "$(uname -v | grep Ubuntu | cut -d- -f2 | cut -d' ' -f1)x" == "Ubuntux" ]; then
    export HOST_IP=$(ip -o addr show | grep inet\ | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' | egrep -v '^127.0.0.1'  | grep 172 | head -1)
else
    export HOST_IP=$( ip -o addr show | grep "inet " | cut -d: -f2- | cut -c2- | egrep -v "^docker|^br" | grep "$(ip route list | grep default | cut -d' ' -f5) " | cut -d' ' -f6 | cut -d/ -f1)
fi

echo "HOST_IP: $HOST_IP"

docker-compose down
docker-compose ps
echo "docker ps : $?"

docker rmi -f docker-io.dbc.dk/rawrepo-postgres-${RAWREPO_VERSION}:${USER}
docker rmi -f docker-io.dbc.dk/holdings-items-postgres-${HOLDINGS_ITEMS_VERSION}:${USER}
docker rmi -f docker-i.dbc.dk/update-postgres:${USER}
docker rmi -f docker-io.dbc.dk/opencat-business:${USER}
docker rmi -f docker-io.dbc.dk/rawrepo-record-service:${USER}
if [ "$USE_LOCAL_PAYARA" = "N" ]
then
    docker rmi -f docker-i.dbc.dk/update-payara:${USER}
fi
docker-compose pull
docker-compose up -d rawrepoDb
docker-compose up -d updateserviceDb
docker-compose up -d holdingsitemsDb
docker-compose up -d fakeSmtp

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
echo "rawrepo.db.url = rawrepo:thePassword@${HOST_IP}:${RAWREPO_PORT}/rawrepo" >> ${HOME}/.ocb-tools/testrun.properties

echo "holdings.jdbc.driver = org.postgresql.Driver" >> ${HOME}/.ocb-tools/testrun.properties
echo "holdings.jdbc.conn.url = jdbc:postgresql://${HOST_IP}:${HOLDINGSITEMSDB_PORT}/holdingsitems" >> ${HOME}/.ocb-tools/testrun.properties
echo "holdings.jdbc.conn.user = holdingsitems" >> ${HOME}/.ocb-tools/testrun.properties
echo "holdings.jdbc.conn.passwd = thePassword" >> ${HOME}/.ocb-tools/testrun.properties
echo "holdings.db.url = holdingsitems:thePassword@${HOST_IP}:${HOLDINGSITEMSDB_PORT}/holdingsitems" >> ${HOME}/.ocb-tools/testrun.properties

echo "updateservice.db.url = updateservice:thePassword@${HOST_IP}:${UPDATESERVICEDB_PORT}/updateservice" >> ${HOME}/.ocb-tools/testrun.properties

echo "solr.port = ${SOLR_PORT_NR}" >> ${HOME}/.ocb-tools/testrun.properties

#Set x.forwarded.for to the ip of devel8. This way it is possible to run the test behind the vpn
echo "request.headers.x.forwarded.for = 172.17.20.165" >> ${HOME}/.ocb-tools/testrun.properties

echo "rawrepo.provider.name.dbc = dataio-update" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.provider.name.fbs = opencataloging-update" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.provider.name.ph = fbs-ph-update" >> ${HOME}/.ocb-tools/testrun.properties
echo "rawrepo.provider.name.ph.holdings = dataio-ph-holding-update" >> ${HOME}/.ocb-tools/testrun.properties

export DEV_VIPCORE_ENDPOINT="http://${HOST_IP}:${SOLR_PORT_NR}"
export DEV_NUMBERROLL_URL="http://${HOST_IP}:${SOLR_PORT_NR}"
export DEV_RAWREPO_DB_URL="rawrepo:thePassword@${HOST_IP}:${RAWREPO_PORT}/rawrepo"
export DEV_HOLDINGS_ITEMS_DB_URL="holdingsitems:thePassword@${HOST_IP}:${HOLDINGSITEMSDB_PORT}/holdingsitems"

#opencat-business-service
docker-compose up -d rawrepo-record-service
RAWREPO_RECORD_SERVICE_CONTAINER=`docker-compose ps -q rawrepo-record-service`
export RAWREPO_RECORD_SERVICE_PORT=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${RAWREPO_RECORD_SERVICE_CONTAINER} `
echo -e "RAWREPO_RECORD_SERVICE_PORT is ${RAWREPO_RECORD_SERVICE_PORT}\n"
echo "rawrepo.record.service.url = http://${HOST_IP}:${RAWREPO_RECORD_SERVICE_PORT}" >> ${HOME}/.ocb-tools/testrun.properties

export DEV_RAWREPO_RECORD_SERVICE_URL="http://${HOST_IP}:${RAWREPO_RECORD_SERVICE_PORT}"
export DEV_SOLR_URL="http://${HOST_IP}:${SOLR_PORT_NR}/solr/raapost-index"
export DEV_SOLR_BASIS_URL="http://${HOST_IP}:${SOLR_PORT_NR}/solr/basis-index"

docker-compose up -d opencat-business-service
OPENCAT_BUSINESS_SERVICE_CONTAINER=`docker-compose ps -q opencat-business-service`
export OPENCAT_BUSINESS_SERVICE_PORT=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${OPENCAT_BUSINESS_SERVICE_CONTAINER} `
echo -e "OPENCAT_BUSINESS_SERVICE_PORT is ${OPENCAT_BUSINESS_SERVICE_PORT}\n"
echo "opencat.business.url = http://${HOST_IP}:${OPENCAT_BUSINESS_SERVICE_PORT}" >> ${HOME}/.ocb-tools/testrun.properties

docker tag docker-io.dbc.dk/opencat-business:${OPENCAT_BUSINESS_SERVICE_TAG} docker-io.dbc.dk/opencat-business:${USER}
docker rmi docker-io.dbc.dk/opencat-business:${OPENCAT_BUSINESS_SERVICE_TAG}
docker tag docker-io.dbc.dk/rawrepo-record-service:${RAWREPO_RECORD_SERVICE_TAG} docker-io.dbc.dk/rawrepo-record-service:${USER}
docker rmi docker-io.dbc.dk/rawrepo-record-service:${RAWREPO_RECORD_SERVICE_TAG}

#Look in start-local-docker.sh for final configuration
echo "updateservice.url = dummy" >> ${HOME}/.ocb-tools/testrun.properties
echo "buildservice.url = dummy" >> ${HOME}/.ocb-tools/testrun.properties

../../bin/healthcheck-rawrepo-record-service.sh ${HOST_IP} ${RAWREPO_RECORD_SERVICE_PORT} 220 || die "could not start rawrepo-record-service"
../../bin/healthcheck-opencat-business-service.sh ${HOST_IP} ${OPENCAT_BUSINESS_SERVICE_PORT} 220 || die "could not start opencat-business-service"
