#!/usr/bin/env bash
set -x

PWD="$(pwd)"
IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))
DOCKER_BIN="${IDEA_ROOT}/docker/bin"
. "${DOCKER_BIN}/common.sh"

DOCKER_COMPOSE_CMD="$(command -v docker-compose > /dev/null && echo docker-compose || echo docker compose)"

if [[ -e ${TEST_RUN} ]]
then
    echo "Found testrun.properties so starting"
else
    echo "ERROR!"
    echo "~/.ocb-tools/testrun.properties must exist in order to run this script, but the file is missing."
    echo "A possible cause for this is that start_dev_docker.sh has not been executed yet."
    exit 1
fi

export SOLR_PORT_NR=$(grep solr.port ${HOME}/.ocb-tools/testrun.properties | awk '{print $3}')
DOCKER_FOLDER=${IDEA_ROOT}/docker/update-payara-dev

export HOST_IP=$(getHostIP)
echo "HOST_IP: $HOST_IP"

cd ${IDEA_ROOT}
mvn verify install -Dmaven.test.skip=true

rm "${DOCKER_FOLDER}/*.war"
cp ${IDEA_ROOT}/target/updateservice-2.0-SNAPSHOT.war ${DOCKER_FOLDER}

docker build -t docker-metascrum.artifacts.dbccloud.dk/update-payara-dev:latest ${DOCKER_FOLDER}

cd docker/deployments/dev

export COMPOSE_PROJECT_NAME=${USER}

DEV_NUMBERROLL_URL=${DEV_NUMBERROLL_URL:-NOTSET}
if [ ${DEV_NUMBERROLL_URL} = "NOTSET" ]
then
    export DEV_NUMBERROLL_URL="http://${HOST_IP}:${SOLR_PORT_NR}"
fi

DEV_VIPCORE_ENDPOINT="${DEV_VIPCORE_ENDPOINT:-http://${HOST_IP}:${SOLR_PORT_NR}}"
DEV_IDP_SERVICE_URL="${DEV_IDP_SERVICE_URL:-http://${HOST_IP}:${SOLR_PORT_NR}}"

# Solr FBS settings
export DEV_SOLR_ADDR="${DEV_SOLR_ADDR:-${HOST_IP}}"
export DEV_SOLR_PORT=${SOLR_PORT_NR}:-""}
export DEV_SOLR_PATH="${DEV_SOLR_PATH:-solr/raapost-index}"
export DEV_SOLR_URL="http://${DEV_SOLR_ADDR}:${DEV_SOLR_PORT}/${DEV_SOLR_PATH}"

#Solr basis settings
export DEV_SOLR_BASIS_ADDR="${DEV_SOLR_BASIS_ADDR:-${HOST_IP}}"
export DEV_SOLR_BASIS_PORT="${DEV_SOLR_BASIS_PORT:-${SOLR_PORT_NR}}"
export DEV_SOLR_BASIS_PATH="${DEV_SOLR_BASIS_PATH:-solr/basis-index}"
export DEV_SOLR_BASIS_URL="http://${DEV_SOLR_BASIS_ADDR}:${DEV_SOLR_BASIS_PORT}/${DEV_SOLR_BASIS_PATH}"

export DEV_RAWREPO_DB_URL=$(grep rawrepo.db.url ${TEST_RUN} | awk '{print $3}')
export DEV_UPDATE_DB_URL=$(grep updateservice.db.url ${TEST_RUN} | awk '{print $3}')
export DEV_OPENCAT_BUSINESS_URL=$(grep opencat.business.url ${TEST_RUN} | awk '{print $3}')
export DEV_HOLDINGS_URL=$(grep holdings.url ${TEST_RUN} | awk '{print $3}')

echo -e "Rawrepo db : ${DEV_RAWREPO_DB_URL}"
echo -e "Updateservice db : ${DEV_RAWREPO_DB_URL}"
echo -e "Opencat-business url : ${DEV_OPENCAT_BUSINESS_URL}"

echo "BONGO $DOCKER_COMPOSE_CMD"
${DOCKER_COMPOSE_CMD} stop updateservice
${DOCKER_COMPOSE_CMD} stop updateservice-facade
${DOCKER_COMPOSE_CMD} up -d updateservice

UPDATESERVICE_IMAGE=$(${DOCKER_COMPOSE_CMD} ps -q updateservice)
UPDATESERVICE_PORT_8080=$(docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATESERVICE_IMAGE} )
echo -e "UPDATESERVICE_PORT_8080 is ${UPDATESERVICE_PORT_8080}\n"
UPDATESERVICE_PORT_8686=$(docker inspect --format='{{(index (index .NetworkSettings.Ports "8686/tcp") 0).HostPort}}' ${UPDATESERVICE_IMAGE} )
echo -e "UPDATESERVICE_PORT_8686 is ${UPDATESERVICE_PORT_8686}\n"
UPDATESERVICE_PORT_4848=$(docker inspect --format='{{(index (index .NetworkSettings.Ports "4848/tcp") 0).HostPort}}' ${UPDATESERVICE_IMAGE} )
echo -e "UPDATESERVICE_PORT_4848 is ${UPDATESERVICE_PORT_4848}\n"

export UPDATE_SERVICE_URL="http://${HOST_IP}:${UPDATESERVICE_PORT_8080}/UpdateService/rest"
export BUILD_SERVICE_URL="http://${HOST_IP}:${UPDATESERVICE_PORT_8080}/UpdateService/rest"

${DOCKER_COMPOSE_CMD} up -d updateservice-facade

UPDATESERVICE_FACADE_IMAGE=`${DOCKER_COMPOSE_CMD} ps -q updateservice-facade`
UPDATESERVICE_FACADE_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATESERVICE_FACADE_IMAGE} `
echo -e "UPDATESERVICE_FACADE_PORT_8080 is ${UPDATESERVICE_FACADE_PORT_8080}\n"
UPDATESERVICE_FACADE_PORT_8686=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8686/tcp") 0).HostPort}}' ${UPDATESERVICE_FACADE_IMAGE} `
echo -e "UPDATESERVICE_FACADE_PORT_8686 is ${UPDATESERVICE_FACADE_PORT_8686}\n"
UPDATESERVICE_FACADE_PORT_4848=`docker inspect --format='{{(index (index .NetworkSettings.Ports "4848/tcp") 0).HostPort}}' ${UPDATESERVICE_FACADE_IMAGE} `
echo -e "UPDATESERVICE_FACADE_PORT_4848 is ${UPDATESERVICE_FACADE_PORT_4848}\n"

"${DOCKER_BIN}/healthcheck-update-service.sh" ${HOST_IP} ${UPDATESERVICE_PORT_8080} 220 || fail "could not start update-service"
"${DOCKER_BIN}/healthcheck-update-facade-service.sh" ${HOST_IP} ${UPDATESERVICE_FACADE_PORT_8080} 220 || fail "could not start update-facade-service"
cd "${PWD}"

sed -i -e "/^buildservice.url/s/^.*$/buildservice.url = http:\/\/${HOST_IP}:${UPDATESERVICE_FACADE_PORT_8080}/" ${HOME}/.ocb-tools/testrun.properties
sed -i -e "/^updateservice.url/s/^.*$/updateservice.url = http:\/\/${HOST_IP}:${UPDATESERVICE_FACADE_PORT_8080}/" ${HOME}/.ocb-tools/testrun.properties
