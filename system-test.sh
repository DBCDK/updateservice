#!/usr/bin/env bash

function die() {
  echo "systest ---> Error: $@ failed"
  collect_logs
  docker-compose down
  exit 1
}

function collect_logs () {
  echo "systest ---> Collect log files"
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-rawrepo-db_1 > logs/pg-rawrepo.log
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-holdingsitems-db_1 > logs/pg-holdingsitems.log
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-update-db_1 > logs/pg-updatedb.log
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-updateservice_1 > logs/gf-updateservice.log
  docker logs ${COMPOSE_PROJECT_NAME}_ocb-tools-systemtests_1 > logs/ocb-tools.log
}

function removeImages() {
  echo "systest ---> Removing old images"
  docker rmi 'docker-os.dbc.dk/rawrepo-postgres-1.13-snapshot:'${COMPOSE_PROJECT_NAME}
  docker rmi 'docker-os.dbc.dk/holdings-items-postgres-1.1.1-snapshot:'${COMPOSE_PROJECT_NAME}
  docker rmi 'docker-i.dbc.dk/fakesmtp:latest'
}

function startContainers () {
  echo "systest ---> Starting containers"
  docker-compose up -d update-systemtests-rawrepo-db       || die "docker-compose up -d update-systemtests-rawrepo-db"
  docker-compose up -d update-systemtests-holdingsitems-db || die "docker-compose up -d update-systemtests-holdingsitems-db"
  docker-compose up -d update-systemtests-update-db        || die "docker-compose up -d update-systemtests-update-db"
  docker-compose up -d update-systemtests-fake-smtp        || die "docker-compose up -d update-systemtests-fake-smtp"
  docker-compose up -d update-systemtests-updateservice    || die "docker-compose up -d update-systemtests-updateservice"
}

function reTagAndRemove () {
  echo "systest ---> retagging and removing containers"
  RAWREPO_DB_VERSION=1.12
  HOLDINGS_DB_VERION=1.1.1
  docker tag docker-os.dbc.dk/rawrepo-postgres-${RAWREPO_DB_VERSION}-snapshot:latest docker-os.dbc.dk/rawrepo-postgres-${RAWREPO_DB_VERSION}-snapshot:${COMPOSE_PROJECT_NAME}
  docker rmi docker-os.dbc.dk/rawrepo-postgres-${RAWREPO_DB_VERSION}-snapshot:latest
  docker tag docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_DB_VERION}-snapshot:latest docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_DB_VERION}-snapshot:${COMPOSE_PROJECT_NAME}
  docker rmi docker-os.dbc.dk/holdings-items-postgres-${HOLDINGS_DB_VERION}-snapshot:latest
}

function setupLogAndLogdir () {
  echo "systest ---> Setting up logdirs"
  cd ${SYSTEST_PATH} || die "cd ${SYSTEST_PATH}"
  rm -rf logs  || die "rm -rf logs"
  mkdir -p logs/updateservice || die "mkdir -p logs/updateservice"
  mkdir -p logs/update || die "mkdir -p logs/update"
  mkdir -p logs/fakesmtp || die "mkdir -p logs/fakesmtp"
  cp ../../../update-logback-include.xml logs/updateservice/  || die "cp ../../../update-logback-include.xml logs/updateservice/"
  mkdir -p logs/ocb-tools || die "mkdir -p logs/ocb-tools"
  chmod -R a+rw logs || die "chmod -R a+rw logs"
}

function waitForOk () {
  echo "systest ---> waiting on containers"
  UPDATE_SERVICE_IMAGE=`docker-compose ps -q update-systemtests-updateservice`
  UPDATESERVICE_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATE_SERVICE_IMAGE} `
  echo -e "systest ---> UPDATESERVICE_PORT_8080 is $UPDATESERVICE_PORT_8080\n"
  echo "systest ---> Wait for glassfish containers"
  ../../bin/return-when-status-ok.sh ${HOST_IP} ${UPDATESERVICE_PORT_8080} 220 '[updateservice]' || die "could not start updateservice"
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

