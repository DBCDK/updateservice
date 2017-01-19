#!/usr/bin/env bash

function die() {
    echo "Error: $@ failed"
    exit 1
}

if [ "$1" == "payara" ]; then
    echo "Running in payara mode: "
    SYSTEST_PATH="docker/deployments/systemtests-payara"
else
    echo "Running in glassfish mode: "
    SYSTEST_PATH="docker/deployments/systemtests"
fi

COMPOSE_PROJECT_NAME=systemtests
export HOST_IP=$(ip addr show | grep -A 99 '^2' | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' |grep -v '^127.0.0.1' | head -1)
echo "Using host IP: ${HOST_IP}"

cd ${SYSTEST_PATH} || die "cd ${SYSTEST_PATH}"
rm -rf logs  || die "rm -rf logs"

mkdir -p logs/dataio || die "mkdir -p logs/dataio"
mkdir -p logs/fbs || die "mkdir -p logs/fbs"
cp ../../../update-logback-include.xml logs/dataio/  || die "cp ../../../update-logback-include.xml logs/dataio/"
cp ../../../update-logback-include.xml logs/fbs/ || die "cp ../../../update-logback-include.xml logs/fbs/"

mkdir -p logs/ocb-tools || die "mkdir -p logs/ocb-tools"

chmod -R a+rw logs || die "chmod -R a+rw logs"

echo "Stop glassfish containers"
docker-compose down || die "docker-compose down"

echo "Removing old images"
../../bin/remove-images docker-i.dbc.dk*
../../bin/remove-dangling-images

sleep 3 || die "sleep 3"
echo "Startup glassfish containers"
docker-compose up -d update-systemtests-rawrepo-db \
                     update-systemtests-holdingsitems-db \
                     update-systemtests-update-db \
                     update-systemtests-fake-smtp \
                     update-systemtests-fbs \
                     update-systemtests-dataio  || die "docker-compose up -d update-systemtests-rawrepo-db \ update-systemtests-holdingsitems-db \ update-systemtests-update-db \  update-systemtests-fake-smtp \ update-systemtests-fbs \ update-systemtests-dataio"

sleep 10 || die "sleep 10"
docker tag docker-i.dbc.dk/mock-rawrepo-postgres:latest docker-i.dbc.dk/mock-rawrepo-postgres:systemtest
docker rmi docker-i.dbc.dk/mock-rawrepo-postgres:latest
docker tag docker-i.dbc.dk/mock-holdingsitems-postgres:latest docker-i.dbc.dk/mock-holdingsitems-postgres:systemtest
docker rmi docker-i.dbc.dk/mock-holdingsitems-postgres:latest

UPDATEDATAIO_IMAGE=`docker-compose ps -q update-systemtests-dataio`
UPDATEDATAIO_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATEDATAIO_IMAGE} `
echo -e "UPDATEDATAIO_PORT_8080 is $UPDATEDATAIO_PORT_8080\n"
UPDATEFBS_IMAGE=`docker-compose ps -q update-systemtests-fbs`
UPDATEFBS_PORT_8080=`docker inspect --format='{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' ${UPDATEFBS_IMAGE} `
echo -e "UPDATEFBS_PORT_8080 is $UPDATEFBS_PORT_8080\n"
echo "Wait for glassfish containers"
../../bin/return-when-status-ok.sh ${HOST_IP} ${UPDATEDATAIO_PORT_8080} '[dataio]' || die "could not start dataio"
../../bin/return-when-status-ok.sh ${HOST_IP} ${UPDATEFBS_PORT_8080} '[fbs]' || die "could not start fbs"

sleep 3 || die "sleep 3"

echo "Start and run systemtests"
docker-compose up ocb-tools-systemtests || die "docker-compose up ocb-tools-systemtests"

echo "Collect log files"
docker logs systemtests_update-systemtests-rawrepo-db_1 > logs/pg-rawrepo.log
docker logs systemtests_update-systemtests-holdingsitems-db_1 > logs/pg-holdingsitems.log
docker logs systemtests_update-systemtests-update-db_1 > logs/pg-updatedb.log
docker logs systemtests_update-systemtests-fbs_1 > logs/gf-fbs.log
docker logs systemtests_update-systemtests-dataio_1 > logs/gf-dataio.log
docker logs systemtests_ocb-tools-systemtests_1 > logs/ocb-tools.log

sleep 3 || die "sleep 3"
echo "Stop glassfish containers"
docker-compose down || die "docker-compose down"