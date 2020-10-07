#!/usr/bin/env bash

function die() {
  echo "systest ---> Error: $@ failed"
  collect_logs
  docker-compose down
  exit 1
}

function collect_logs () {
  echo "systest ---> Collect log files"
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-updateservice_1 > logs/gf-updateservice.log
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-updateservice-facade_1 > logs/gf-updateservice-facade.log
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-opencat-business-service_1 > logs/gf-opencat-business-service.log
  docker logs ${COMPOSE_PROJECT_NAME}_ocb-tools-systemtests_1 > logs/ocb-tools.log
}

function removeImages() {
  echo "systest ---> Removing old images"
  docker rmi docker-io.dbc.dk/rawrepo-postgres-1.13-snapshot:${COMPOSE_PROJECT_NAME}
  docker rmi docker-os.dbc.dk/holdings-items-postgres-1.1.4:${COMPOSE_PROJECT_NAME}
  docker rmi docker-io.dbc.dk/updateservice-facade:${COMPOSE_PROJECT_NAME}
  docker rmi docker-io.dbc.dk/opencat-business:${COMPOSE_PROJECT_NAME}
  docker rmi docker-io.dbc.dk/rawrepo-record-service:${COMPOSE_PROJECT_NAME}
  docker rmi docker-i.dbc.dk/fakesmtp:latest
}

function startContainers () {
  echo "systest ---> Starting containers"
  docker-compose up -d update-systemtests-rawrepo-db                  || die "docker-compose up -d update-systemtests-rawrepo-db"
  docker-compose up -d update-systemtests-holdingsitems-db            || die "docker-compose up -d update-systemtests-holdingsitems-db"
  docker-compose up -d update-systemtests-update-db                   || die "docker-compose up -d update-systemtests-update-db"
  docker-compose up -d update-systemtests-fake-smtp                   || die "docker-compose up -d update-systemtests-fake-smtp"
  docker-compose up -d update-systemtests-updateservice               || die "docker-compose up -d update-systemtests-updateservice"
  docker-compose up -d update-systemtests-updateservice-facade        || die "docker-compose up -d update-systemtests-updateservice-facade"
  docker-compose up -d update-systemtests-rawrepo-record-service      || die "docker-compose up -d update-systemtests-rawrepo-record-service"
  docker-compose up -d update-systemtests-opencat-business-service    || die "docker-compose up -d update-systemtests-opencat-business-service"
}

function reTagAndRemove () {
  echo "systest ---> retagging and removing containers"
  RAWREPO_DB_VERSION=1.12
  HOLDINGS_DB_VERION=1.1.4
  UPDATESERVICE_FACADE_TAG=master-31
  OPENCAT_BUSINESS_SERVICE_TAG=latest
  RAWREPO_RECORD_SERVICE_TAG=DIT-264
  docker tag docker-io.dbc.dk/rawrepo-postgres-${RAWREPO_DB_VERSION}-snapshot:latest docker-io.dbc.dk/rawrepo-postgres-${RAWREPO_DB_VERSION}-snapshot:${COMPOSE_PROJECT_NAME}
  docker rmi docker-io.dbc.dk/rawrepo-postgres-${RAWREPO_DB_VERSION}-snapshot:latest
  docker tag docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_DB_VERION}-snapshot:latest docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_DB_VERION}-snapshot:${COMPOSE_PROJECT_NAME}
  docker rmi docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_DB_VERION}-snapshot:latest
  docker tag docker-io.dbc.dk/updateservice-facade:${UPDATESERVICE_FACADE_TAG} docker-io.dbc.dk/updateservice-facade:${COMPOSE_PROJECT_NAME}
  docker rmi docker-io.dbc.dk/updateservice-facade:${UPDATESERVICE_FACADE_TAG}
  docker tag docker-io.dbc.dk/opencat-business:${OPENCAT_BUSINESS_SERVICE_TAG} docker-io.dbc.dk/opencat-business:${COMPOSE_PROJECT_NAME}
  docker rmi docker-io.dbc.dk/opencat-business:${OPENCAT_BUSINESS_SERVICE_TAG}
  docker tag docker-io.dbc.dk/rawrepo-record-service:${RAWREPO_RECORD_SERVICE_TAG} docker-io.dbc.dk/rawrepo-record-service:${COMPOSE_PROJECT_NAME}
  docker rmi docker-io.dbc.dk/rawrepo-record-service:${RAWREPO_RECORD_SERVICE_TAG}
}

function setupLogAndLogdir () {
  echo "systest ---> Setting up logdirs"
  cd ${SYSTEST_PATH} || die "cd ${SYSTEST_PATH}"
  rm -rf logs  || die "rm -rf logs"
  mkdir -p logs/ocb-tools || die "mkdir -p logs/ocb-tools"
  chmod -R a+rw logs || die "chmod -R a+rw logs"
}

function waitForOk () {
  echo "systest ---> waiting on containers"
  RAWREPO_RECORD_SERVICE_CONTAINER=`docker-compose ps -q update-systemtests-rawrepo-record-service`
  RAWREPO_RECORD_SERVICE_PORT=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${RAWREPO_RECORD_SERVICE_CONTAINER} `
  echo -e "RAWREPO_RECORD_SERVICE_PORT is ${RAWREPO_RECORD_SERVICE_PORT}\n"
  OPENCAT_BUSINESS_SERVICE_CONTAINER=`docker-compose ps -q update-systemtests-opencat-business-service`
  OPENCAT_BUSINESS_SERVICE_PORT=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${OPENCAT_BUSINESS_SERVICE_CONTAINER} `
  echo -e "OPENCAT_BUSINESS_SERVICE_PORT is ${OPENCAT_BUSINESS_SERVICE_PORT}\n"
  UPDATE_SERVICE_CONTAINER=`docker-compose ps -q update-systemtests-updateservice`
  UPDATE_SERVICE_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATE_SERVICE_CONTAINER} `
  echo -e "systest ---> UPDATE_SERVICE_PORT_8080 is $UPDATE_SERVICE_PORT_8080\n"
  UPDATESERVICE_FACADE_CONTAINER=`docker-compose ps -q update-systemtests-updateservice-facade`
  UPDATESERVICE_FACADE_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATESERVICE_FACADE_CONTAINER} `
  echo -e "UPDATESERVICE_FACADE_PORT_8080 is ${UPDATESERVICE_FACADE_PORT_8080}\n"
  echo "systest ---> Wait for glassfish containers"
  ../../bin/healthcheck-rawrepo-record-service.sh ${HOST_IP} ${RAWREPO_RECORD_SERVICE_PORT} 220 || die "could not start rawrepo-record-service"
  ../../bin/healthcheck-opencat-business-service.sh ${HOST_IP} ${OPENCAT_BUSINESS_SERVICE_PORT} 220 || die "could not start opencat-business-service"
  ../../bin/healthcheck-update-service.sh ${HOST_IP} ${UPDATE_SERVICE_PORT_8080} 220 || die "could not start update-service"
  ../../bin/healthcheck-update-facade-service.sh ${HOST_IP} ${UPDATESERVICE_FACADE_PORT_8080} 220 || die "could not start update-facade-service"
  echo "systest ---> Sleeping 3"
  sleep 3 || die "sleep 3"
}

function setSysVars () {
  echo "systest ---> Setting systest variables"
  export SYSTEST_PATH="docker/deployments/systemtests-payara"
  export COMPOSE_PROJECT_NAME=systemtestspayara
  export HOST_IP=$(ip addr show | grep -A 99 '^2' | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' |grep -v '^127.0.0.1' | head -1)
  export UPDATE_PAYARA_TAG=$1
  echo "systest ---> Using host IP: ${HOST_IP}"
}

function main ()  {
  setSysVars $1
  setupLogAndLogdir
  echo "systest ---> systest ---> Stop glassfish containers"
  docker-compose down
  removeImages
  startContainers
  reTagAndRemove
  waitForOk
  echo "systest ---> Start and run systemtests"
  docker-compose up ocb-tools-systemtests || die "docker-compose up ocb-tools-systemtests"
  collect_logs
  sleep 3 || die "sleep 3"
  echo "systest ---> Stop glassfish containers"
  docker-compose down || die "docker-compose down"
}

main $1

