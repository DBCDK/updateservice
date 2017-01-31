#!/usr/bin/env bash

if [ "$1" == "payara" ]; then
    echo "systest ---> Running in payara mode: "
    SYSTEST_PATH="docker/deployments/systemtests-payara"
    export COMPOSE_PROJECT_NAME=systemtestspayara
    export systest="-payara"
else
    echo "systest ---> Running in glassfish mode: "
    SYSTEST_PATH="docker/deployments/systemtests"
    export COMPOSE_PROJECT_NAME=systemtests
    export systest="-glassfish"
fi

function collect_logs () {
   echo "systest ---> Collect log files"
   docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-rawrepo-db${systest}_1 > logs/pg-rawrepo.log
   docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-holdingsitems-db${systest}_1 > logs/pg-holdingsitems.log
   docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-update-db${systest}_1 > logs/pg-updatedb.log
   docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-fbs_1 > logs/gf-fbs.log
   docker logs ${COMPOSE_PROJECT_NAME}_update-systemtests-dataio_1 > logs/gf-dataio.log
   docker logs ${COMPOSE_PROJECT_NAME}_ocb-tools-systemtests_1 > logs/ocb-tools.log
}
function die() {
    echo "systest ---> Error: $@ failed"
    collect_logs
    docker-compose down
    exit 1
}

export HOST_IP=$(ip addr show | grep -A 99 '^2' | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' |grep -v '^127.0.0.1' | head -1)
echo "systest ---> Using host IP: ${HOST_IP}"

cd ${SYSTEST_PATH} || die "cd ${SYSTEST_PATH}"
rm -rf logs  || die "rm -rf logs"

mkdir -p logs/dataio || die "mkdir -p logs/dataio"
mkdir -p logs/fbs || die "mkdir -p logs/fbs"
cp ../../../update-logback-include.xml logs/dataio/  || die "cp ../../../update-logback-include.xml logs/dataio/"
cp ../../../update-logback-include.xml logs/fbs/ || die "cp ../../../update-logback-include.xml logs/fbs/"

mkdir -p logs/ocb-tools || die "mkdir -p logs/ocb-tools"

chmod -R a+rw logs || die "chmod -R a+rw logs"

echo "systest ---> Stop glassfish containers"
docker-compose down

#echo "Removing old images"
#docker rmi 'docker-i.dbc.dk/mock-rawrepo-postgres:latest'
#docker rmi 'docker-i.dbc.dk/mock-holdingsitems-postgres:latest'
#docker rmi 'docker-i.dbc.dk/fakesmtp:latest'
#docker rmi 'docker-i.dbc.dk/update-postgres:candidate'
#docker rmi 'docker-i.dbc.dk/update-*:candidate'
#docker rmi 'docker-i.dbc.dk/ocb-tools-deployer:latest'

echo "Startup glassfish containers here : `pwd`"
docker-compose up -d update-systemtests-rawrepo-db${systest} \
                     update-systemtests-holdingsitems-db${systest} \
                     update-systemtests-update-db${systest} \
                     update-systemtests-fake-smtp \
                     update-systemtests-fbs \
                     update-systemtests-dataio  || die "docker-compose up -d update-systemtests-rawrepo-db \ update-systemtests-holdingsitems-db \ update-systemtests-update-db \  update-systemtests-fake-smtp \ update-systemtests-fbs \ update-systemtests-dataio"

#echo "Startup glassfish containers here : `pwd`"
#docker-compose up -d update-systemtests-rawrepo-db${systest} || die "rawrepo"
#docker-compose up -d                     update-systemtests-holdingsitems-db${systest} || die "holdingsitems"
#docker-compose up -d                     update-systemtests-update-db${systest} || die "updatedb"
#docker-compose up -d                     update-systemtests-fake-smtp || die "smtp"
#docker-compose up -d                     update-systemtests-fbs || die "fbs"
#docker-compose up -d                     update-systemtests-dataio  || die "dataio"

docker tag docker-i.dbc.dk/mock-rawrepo-postgres:latest docker-i.dbc.dk/mock-rawrepo-postgres:${COMPOSE_PROJECT_NAME}
docker rmi docker-i.dbc.dk/mock-rawrepo-postgres:latest
docker tag docker-i.dbc.dk/mock-holdingsitems-postgres:latest docker-i.dbc.dk/mock-holdingsitems-postgres:${COMPOSE_PROJECT_NAME}
docker rmi docker-i.dbc.dk/mock-holdingsitems-postgres:latest

UPDATEDATAIO_IMAGE=`docker-compose ps -q update-systemtests-dataio`
UPDATEDATAIO_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATEDATAIO_IMAGE} `
echo -e "systest ---> UPDATEDATAIO_PORT_8080 is $UPDATEDATAIO_PORT_8080\n"
UPDATEFBS_IMAGE=`docker-compose ps -q update-systemtests-fbs`
UPDATEFBS_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATEFBS_IMAGE} `
echo -e "systest ---> UPDATEFBS_PORT_8080 is $UPDATEFBS_PORT_8080\n"
echo "systest ---> Wait for glassfish containers"

../../bin/return-when-status-ok.sh ${HOST_IP} ${UPDATEDATAIO_PORT_8080} 220 '[dataio]' || die "could not start dataio"
../../bin/return-when-status-ok.sh ${HOST_IP} ${UPDATEFBS_PORT_8080} 220 '[fbs]' || die "could not start fbs"

sleep 3 || die "sleep 3"

echo "systest ---> Start and run systemtests"
docker-compose up ocb-tools-systemtests || die "docker-compose up ocb-tools-systemtests"

collect_logs

sleep 3 || die "sleep 3"
echo "systest ---> Stop glassfish containers"
docker-compose down || die "docker-compose down"
