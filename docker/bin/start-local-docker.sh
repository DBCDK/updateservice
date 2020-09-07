#!/usr/bin/env bash
#set -x

function die() {
  echo "systest ---> Error: $@ failed"
  exit 1
}

if [[ -e ${HOME}/.ocb-tools/testrun.properties ]]
then
    echo "Found testrun.properties so starting"
else
    echo "ERROR!"
    echo "~/.ocb-tools/testrun.properties must exist in order to run this script, but the file is missing."
    echo "A possible cause for this is that start_dev_docker.sh has not been executed yet."
    exit 1
fi

export SOLR_PORT_NR=$(grep solr.port ${HOME}/.ocb-tools/testrun.properties | awk '{print $3}')
IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))
DOCKER_FOLDER=${IDEA_ROOT}/docker/update-payara-dev

if [ "$(uname)" == "Darwin" ]
then
    export HOST_IP=$(ip addr show | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' | egrep -v '^127.0.0.1' | head -1)
else
    export HOST_IP=$( ip -o addr show | grep "inet " | cut -d: -f2- | cut -c2- | egrep -v "^docker|^br" | grep "$(ip route list | grep default | cut -d' ' -f5) " | cut -d' ' -f6 | cut -d/ -f1)
fi

cd ${IDEA_ROOT}
mvn verify install -Dmaven.test.skip=true

rm ${DOCKER_FOLDER}/*.war
cp ${IDEA_ROOT}/target/updateservice-2.0-SNAPSHOT.war ${DOCKER_FOLDER}

docker build -t docker-i.dbc.dk/update-payara-dev:latest ${DOCKER_FOLDER}

cd docker/deployments/dev

export COMPOSE_PROJECT_NAME=${USER}

DEV_NUMBERROLL_URL=${DEV_NUMBERROLL_URL:-NOTSET}
if [ ${DEV_NUMBERROLL_URL} = "NOTSET" ]
then
    export DEV_NUMBERROLL_URL="http://${HOST_IP}:${SOLR_PORT_NR}"
fi
DEV_OPENAGENCY_URL=${DEV_OPENAGENCY_URL:-NOTSET}
if [ ${DEV_OPENAGENCY_URL} = "NOTSET" ]
then
    export DEV_OPENAGENCY_URL="http://${HOST_IP}:${SOLR_PORT_NR}"
fi

# Solr FBS settings
DEV_SOLR_ADDR=${DEV_SOLR_ADDR:-NOTSET}
if [ ${DEV_SOLR_ADDR} = "NOTSET" ]
then
    export DEV_SOLR_ADDR="solrserver"
fi
DEV_SOLR_PORT=${DEV_SOLR_PORT:-NOTSET}
if [ ${DEV_SOLR_PORT} = "NOTSET" ]
then
    export DEV_SOLR_PORT="${SOLR_PORT_NR}"
fi
DEV_SOLR_PATH=${DEV_SOLR_PATH:-NOTSET}
if [ ${DEV_SOLR_PATH} = "NOTSET" ]
then
    export DEV_SOLR_PATH="solr/raapost-index"
fi

export DEV_SOLR_URL="http://${DEV_SOLR_ADDR}:${DEV_SOLR_PORT}/${DEV_SOLR_PATH}"

#Solr basis settings
DEV_SOLR_BASIS_ADDR=${DEV_SOLR_BASIS_ADDR:-NOTSET}
if [ ${DEV_SOLR_BASIS_ADDR} = "NOTSET" ]
then
    export DEV_SOLR_BASIS_ADDR="solrbasis"
fi
DEV_SOLR_BASIS_PORT=${DEV_SOLR_BASIS_PORT:-NOTSET}
if [ ${DEV_SOLR_BASIS_PORT} = "NOTSET" ]
then
    export DEV_SOLR_BASIS_PORT="${SOLR_PORT_NR}"
fi
DEV_SOLR_BASIS_PATH=${DEV_SOLR_BASIS_PATH:-NOTSET}
if [ ${DEV_SOLR_BASIS_PATH} = "NOTSET" ]
then
    export DEV_SOLR_BASIS_PATH="solr/basis-index"
fi

export DEV_SOLR_BASIS_URL="http://${DEV_SOLR_BASIS_ADDR}:${DEV_SOLR_BASIS_PORT}/${DEV_SOLR_BASIS_PATH}"

export DEV_RAWREPO_DB_URL=$(grep rawrepo.db.url ${HOME}/.ocb-tools/testrun.properties | awk '{print $3}')
export DEV_HOLDINGS_ITEMS_DB_URL=$(grep holdings.db.url ${HOME}/.ocb-tools/testrun.properties | awk '{print $3}')
export DEV_UPDATE_DB_URL=$(grep updateservice.db.url ${HOME}/.ocb-tools/testrun.properties | awk '{print $3}')
echo -e "Rawrepo db : ${DEV_RAWREPO_DB_URL}"
echo -e "Holdings db : ${DEV_HOLDINGS_ITEMS_DB_URL}"
echo -e "Updateservice db : ${DEV_RAWREPO_DB_URL}"

docker-compose stop updateservice
docker-compose stop updateservice-facade
docker-compose up -d updateservice

UPDATESERVICE_IMAGE=`docker-compose ps -q updateservice`
UPDATESERVICE_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATESERVICE_IMAGE} `
echo -e "UPDATESERVICE_PORT_8080 is ${UPDATESERVICE_PORT_8080}\n"
UPDATESERVICE_PORT_8686=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8686/tcp") 0).HostPort}}' ${UPDATESERVICE_IMAGE} `
echo -e "UPDATESERVICE_PORT_8686 is ${UPDATESERVICE_PORT_8686}\n"
UPDATESERVICE_PORT_4848=`docker inspect --format='{{(index (index .NetworkSettings.Ports "4848/tcp") 0).HostPort}}' ${UPDATESERVICE_IMAGE} `
echo -e "UPDATESERVICE_PORT_4848 is ${UPDATESERVICE_PORT_4848}\n"

../../bin/return-when-status-ok.sh ${HOST_IP} ${UPDATESERVICE_PORT_8080} 220 || die "could not start updateservice"

export UPDATE_SERVICE_URL="http://${HOST_IP}:${UPDATESERVICE_PORT_8080}/UpdateService/rest"
export BUILD_SERVICE_URL="http://${HOST_IP}:${UPDATESERVICE_PORT_8080}/UpdateService/rest"

docker-compose up -d updateservice-facade

UPDATESERVICE_FACADE_IMAGE=`docker-compose ps -q updateservice-facade`
UPDATESERVICE_FACADE_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATESERVICE_FACADE_IMAGE} `
echo -e "UPDATESERVICE_FACADE_PORT_8080 is ${UPDATESERVICE_FACADE_PORT_8080}\n"
UPDATESERVICE_FACADE_PORT_8686=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8686/tcp") 0).HostPort}}' ${UPDATESERVICE_FACADE_IMAGE} `
echo -e "UPDATESERVICE_FACADE_PORT_8686 is ${UPDATESERVICE_FACADE_PORT_8686}\n"
UPDATESERVICE_FACADE_PORT_4848=`docker inspect --format='{{(index (index .NetworkSettings.Ports "4848/tcp") 0).HostPort}}' ${UPDATESERVICE_FACADE_IMAGE} `
echo -e "UPDATESERVICE_FACADE_PORT_4848 is ${UPDATESERVICE_FACADE_PORT_4848}\n"

../../bin/return-when-status-ok-facade.sh ${HOST_IP} ${UPDATESERVICE_FACADE_PORT_8080} 220 || die "could not start updateservice-facade"
cd -

sed -i -e "/^buildservice.url/s/^.*$/buildservice.url = http:\/\/${HOST_IP}:${UPDATESERVICE_FACADE_PORT_8080}/" ${HOME}/.ocb-tools/testrun.properties
sed -i -e "/^updateservice.url/s/^.*$/updateservice.url = http:\/\/${HOST_IP}:${UPDATESERVICE_FACADE_PORT_8080}/" ${HOME}/.ocb-tools/testrun.properties
