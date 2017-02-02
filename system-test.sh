#!/usr/bin/env bash

function die() {
  echo "systest ---> Error: $@ failed"
  collect_logs
  docker-compose down
  exit 1
}

function collect_logs () {
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-rawrepo-db${systest}_1 > logs/pg-rawrepo.log
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-holdingsitems-db${systest}_1 > logs/pg-holdingsitems.log
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-update-db${systest}_1 > logs/pg-updatedb.log
  docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-updateservice_1 > logs/gf-updateservice.log
  docker logs ${COMPOSE_PROJECT_NAME}_ocb-tools-systemtests_1 > logs/ocb-tools.log
}

function removeImages() {
  docker rmi 'docker-i.dbc.dk/mock-rawrepo-postgres'${systest}':latest'
  docker rmi 'docker-i.dbc.dk/mock-holdingsitems-postgres'${systest}':latest'
  docker rmi 'docker-i.dbc.dk/update-postgres'${systest}':candidate'
  
  docker rmi 'docker-i.dbc.dk/fakesmtp:latest'
  docker rmi 'docker-i.dbc.dk/ocb-tools-deployer:latest'
  
  docker rmi 'docker-i.dbc.dk/update-updateservice:candidate'
}

function startContainers () {
  docker-compose up -d update-systemtests-rawrepo-db${systest}  || die "docker-compose up -d update-systemtests-rawrepo-db${systest}"
  docker-compose up -d update-systemtests-holdingsitems-db${systest}                 || die "docker-compose up -d update-systemtests-holdingsitems-db${systest}"
  docker-compose up -d update-systemtests-update-db${systest}                        || die "docker-compose up -d update-systemtests-update-db${systest}"
  docker-compose up -d update-systemtests-fake-smtp                                  || die  "docker-compose up -d update-systemtests-fake-smtp"
  docker-compose up -d update-systemtests-updateservice                                     || die  "docker-compose up -d update-systemtests-updateservice"
}

function reTagAndRemove () {
  docker tag docker-i.dbc.dk/mock-rawrepo-postgres:latest docker-i.dbc.dk/mock-rawrepo-postgres:${COMPOSE_PROJECT_NAME}
  docker rmi docker-i.dbc.dk/mock-rawrepo-postgres:latest
  docker tag docker-i.dbc.dk/mock-holdingsitems-postgres:latest docker-i.dbc.dk/mock-holdingsitems-postgres:${COMPOSE_PROJECT_NAME}
  docker rmi docker-i.dbc.dk/mock-holdingsitems-postgres:latest
}

function setupLogAndLogdir () {
  cd ${SYSTEST_PATH} || die "cd ${SYSTEST_PATH}"
  rm -rf logs  || die "rm -rf logs"
  mkdir -p logs/updateservice || die "mkdir -p logs/updateservice"
  cp ../../../update-logback-include.xml logs/updateservice/  || die "cp ../../../update-logback-include.xml logs/updateservice/"
  mkdir -p logs/ocb-tools || die "mkdir -p logs/ocb-tools"
  chmod -R a+rw logs || die "chmod -R a+rw logs"
}

function waitForOk () {
  UPDATE_SERVICE_IMAGE=`docker-compose ps -q update-systemtests-updateservice`
  UPDATESERVICE_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATE_SERVICE_IMAGE} `
  echo -e "systest ---> UPDATESERVICE_PORT_8080 is $UPDATESERVICE_PORT_8080\n"
  echo "systest ---> Wait for glassfish containers"
  
  ../../bin/return-when-status-ok.sh ${HOST_IP} ${UPDATESERVICE_PORT_8080} 220 '[updateservice]' || die "could not start updateservice"
}

function setSysVars () {
  if [ "$1" == "payara" ]; then
    echo "systest ---> Running in payara mode: "
    export SYSTEST_PATH="docker/deployments/systemtests-payara"
    export COMPOSE_PROJECT_NAME=systemtestspayara
    export systest="-payara"
  else
    echo "systest ---> Running in glassfish mode: "
    export SYSTEST_PATH="docker/deployments/systemtests"
    export COMPOSE_PROJECT_NAME=systemtests
    export systest="-glassfish"
  fi
  
  export HOST_IP=$(ip addr show | grep -A 99 '^2' | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' |grep -v '^127.0.0.1' | head -1)
  echo "systest ---> Using host IP: ${HOST_IP}"
}

echo "Setting systest variables"
setSysVars ${1}

echo "systest ---> Setting up logdirs"
setupLogAndLogdir

echo "systest ---> systest ---> Stop glassfish containers"
docker-compose down

echo "systest ---> Removing old images"
removeImages

echo "systest ---> Starting containers"
startContainers

echo "systest ---> retagging and removing containers"
reTagAndRemove

echo "systest ---> waiting on containers"
waitForOk

echo "systest ---> Sleeping 3"
sleep 3 || die "sleep 3"

echo "systest ---> Start and run systemtests"
docker-compose up ocb-tools-systemtests || die "docker-compose up ocb-tools-systemtests"

echo "systest ---> Collect log files"
collect_logs

sleep 3 || die "sleep 3"
echo "systest ---> Stop glassfish containers"
docker-compose down || die "docker-compose down"
