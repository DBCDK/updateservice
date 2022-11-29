#!/usr/bin/env bash
#set -x

# If this is set different from N then also change
# image: "${METASCRUM_REPO}"/update-payara:latest
# to
# image: "${METASCRUM_REPO}"/update-payara:mib (or whomever you are)
# in docker-compos.yml
# also go to updateservice/docker/update-payara and make :
# docker build -t "${METASCRUM_REPO}"/update-payara:mib .
USE_LOCAL_PAYARA="Y"

IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))
DOCKER_BIN="${IDEA_ROOT}/docker/bin"
. "${DOCKER_BIN}/common.sh"

SOLR_PORT=$(getFreePort)
HOLDINGS_PORT=$(getFreePort)

DOCKER_COMPOSE_CMD="$(command -v docker-compose > /dev/null && echo docker-compose || echo docker compose)"

RAWREPO_VERSION=1.15-snapshot
RAWREPO_DIT_TAG=DIT-5165
OPENCAT_BUSINESS_SERVICE_TAG=latest
RAWREPO_RECORD_SERVICE_TAG=DIT-330

cd ${IDEA_ROOT}/docker

docker build -t "${METASCRUM_REPO}"/update-payara:latest update-payara \
 || fail "Could not create ${IDEA_ROOT}/docker/logs/update/app ${IDEA_ROOT}/docker/logs/update/server"

cd ${IDEA_ROOT}/docker/deployments/dev || fail "Failed to cd into docker/deployments/dev"

if [ ! -d "${OCB_TOOLS}" ]
then
    mkdir "${OCB_TOOLS}" || fail "Could not create directory ${OCB_TOOLS}"
fi

USER="${USER:-unknown}"
export COMPOSE_PROJECT_NAME=${USER}

#Find the correct outbound ip-address regardless of host configuration
export HOST_IP=$(getHostIP)

echo "HOST_IP: $HOST_IP"

${DOCKER_COMPOSE_CMD} down
${DOCKER_COMPOSE_CMD} ps
echo "docker ps : $?"

docker rmi -f "${METASCRUM_REPO}/rawrepo-postgres-${RAWREPO_VERSION}:${USER}"
docker rmi -f "${METASCRUM_REPO}/update-postgres:${USER}"
docker rmi -f "${METASCRUM_REPO}/opencat-business:${USER}"
docker rmi -f "${METASCRUM_REPO}/rawrepo-record-service:${USER}"
if [ "$USE_LOCAL_PAYARA" = "N" ]
then
    docker rmi -f "${METASCRUM_REPO}/update-payara:${USER}"
fi
${DOCKER_COMPOSE_CMD} pull
${DOCKER_COMPOSE_CMD} up -d rawrepoDb
${DOCKER_COMPOSE_CMD} up -d updateserviceDb
${DOCKER_COMPOSE_CMD} up -d fakeSmtp

docker tag "${METASCRUM_REPO}/rawrepo-postgres-${RAWREPO_VERSION}:${RAWREPO_DIT_TAG}" "${METASCRUM_REPO}/rawrepo-postgres-${RAWREPO_VERSION}:${USER}"
docker rmi "${METASCRUM_REPO}/rawrepo-postgres-${RAWREPO_VERSION}:${RAWREPO_DIT_TAG}"
docker tag "${METASCRUM_REPO}/update-postgres:latest" "${METASCRUM_REPO}/update-postgres:${USER}"
docker rmi "${METASCRUM_REPO}/update-postgres:latest"

RAWREPO_IMAGE=$(${DOCKER_COMPOSE_CMD} ps -q rawrepoDb)
export RAWREPO_PORT=$(docker inspect --format='{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' ${RAWREPO_IMAGE})
echo -e "RAWREPO_PORT is $RAWREPO_PORT\n"

UPDATESERVICEDB_IMAGE=$(${DOCKER_COMPOSE_CMD} ps -q updateserviceDb)
export UPDATESERVICEDB_PORT=$(docker inspect --format='{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' ${UPDATESERVICEDB_IMAGE})
echo -e "UPDATESERVICEDB_PORT is $UPDATESERVICEDB_PORT\n"

clearOcbProperties
appendOcbProperty "rawrepo.jdbc.driver = org.postgresql.Driver"
appendOcbProperty "rawrepo.jdbc.conn.url = jdbc:postgresql://${HOST_IP}:${RAWREPO_PORT}/rawrepo"
appendOcbProperty "rawrepo.jdbc.conn.user = rawrepo"
appendOcbProperty "rawrepo.jdbc.conn.passwd = thePassword"
appendOcbProperty "rawrepo.db.url = rawrepo:thePassword@${HOST_IP}:${RAWREPO_PORT}/rawrepo"

appendOcbProperty "updateservice.db.url = updateservice:thePassword@${HOST_IP}:${UPDATESERVICEDB_PORT}/updateservice"

appendOcbProperty "solr.port = ${SOLR_PORT}"
appendOcbProperty "holdings.port = ${HOLDINGS_PORT}"
export DEV_HOLDINGS_URL="http://${HOST_IP}:${HOLDINGS_PORT}/api"
appendOcbProperty "holdings.url = ${DEV_HOLDINGS_URL}"

#Set x.forwarded.for to the ip of devel8. This way it is possible to run the test behind the vpn
appendOcbProperty "request.headers.x.forwarded.for = 172.17.20.165"

appendOcbProperty "rawrepo.provider.name.dbc = dataio-update"
appendOcbProperty "rawrepo.provider.name.fbs = opencataloging-update"
appendOcbProperty "rawrepo.provider.name.ph = fbs-ph-update"
appendOcbProperty "rawrepo.provider.name.ph.holdings = dataio-ph-holding-update"

export DEV_VIPCORE_ENDPOINT="http://${HOST_IP}:${SOLR_PORT}"
export DEV_NUMBERROLL_URL="http://${HOST_IP}:${SOLR_PORT}"
export DEV_RAWREPO_DB_URL="rawrepo:thePassword@${HOST_IP}:${RAWREPO_PORT}/rawrepo"

#opencat-business-service
${DOCKER_COMPOSE_CMD} up -d rawrepo-record-service
RAWREPO_RECORD_SERVICE_CONTAINER=$(${DOCKER_COMPOSE_CMD} ps -q rawrepo-record-service)
export RAWREPO_RECORD_SERVICE_PORT=$(docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${RAWREPO_RECORD_SERVICE_CONTAINER})
echo -e "RAWREPO_RECORD_SERVICE_PORT is ${RAWREPO_RECORD_SERVICE_PORT}\n"
appendOcbProperty "rawrepo.record.service.url = http://${HOST_IP}:${RAWREPO_RECORD_SERVICE_PORT}"

export DEV_RAWREPO_RECORD_SERVICE_URL="http://${HOST_IP}:${RAWREPO_RECORD_SERVICE_PORT}"
export DEV_SOLR_URL="http://${HOST_IP}:${SOLR_PORT}/solr/raapost-index"
export DEV_SOLR_BASIS_URL="http://${HOST_IP}:${SOLR_PORT}/solr/basis-index"

${DOCKER_COMPOSE_CMD} up -d opencat-business-service
OPENCAT_BUSINESS_SERVICE_CONTAINER=$(${DOCKER_COMPOSE_CMD} ps -q opencat-business-service)
export OPENCAT_BUSINESS_SERVICE_PORT=$(docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${OPENCAT_BUSINESS_SERVICE_CONTAINER})
echo -e "OPENCAT_BUSINESS_SERVICE_PORT is ${OPENCAT_BUSINESS_SERVICE_PORT}\n"
appendOcbProperty "opencat.business.url = http://${HOST_IP}:${OPENCAT_BUSINESS_SERVICE_PORT}"

docker tag "${METASCRUM_REPO}"/opencat-business:${OPENCAT_BUSINESS_SERVICE_TAG} "${METASCRUM_REPO}"/opencat-business:${USER}
docker rmi "${METASCRUM_REPO}"/opencat-business:${OPENCAT_BUSINESS_SERVICE_TAG}
docker tag "${METASCRUM_REPO}"/rawrepo-record-service:${RAWREPO_RECORD_SERVICE_TAG} "${METASCRUM_REPO}"/rawrepo-record-service:${USER}
docker rmi "${METASCRUM_REPO}"/rawrepo-record-service:${RAWREPO_RECORD_SERVICE_TAG}

#Look in start-local-docker.sh for final configuration
appendOcbProperty "updateservice.url = dummy"
appendOcbProperty "buildservice.url = dummy"

"${DOCKER_BIN}/healthcheck-rawrepo-record-service.sh" ${HOST_IP} ${RAWREPO_RECORD_SERVICE_PORT} 220 || fail "could not start rawrepo-record-service"
"${DOCKER_BIN}/healthcheck-opencat-business-service.sh" ${HOST_IP} ${OPENCAT_BUSINESS_SERVICE_PORT} 220 || fail "could not start opencat-business-service"
