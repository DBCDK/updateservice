#!/bin/bash

function die() {
    echo "Error:" "$@"
    exit 1
}

export HOST_IP=$(ip addr show | grep -A 99 '^2' | grep inet | grep -o '[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}\.[0-9]\{1,3\}' | head -1)
echo "Using host IP: ${HOST_IP}"

cd docker/deployments/systemtests || die "cd failed"
rm -rf logs  || die "removing logs failed"

mkdir -p logs/dataio
mkdir -p logs/fbs
cp ../../../update-logback-include.xml logs/dataio/.  || die "copy logs to dataio failed"
cp ../../../update-logback-include.xml logs/fbs/. || die "copy logs to fbs failed"

mkdir -p logs/ocb-tools || die "mkdir logs/ocb-tools failed"

chmod -R +rw logs || die "chmod failed"

echo "Stop glassfish containers"
docker-compose down || die "Docker-compose down failed"

sleep 3 || die "sleep failed"
echo "Startup glassfish containers"
docker-compose up -d update-systemtests-rawrepo-db \
                     update-systemtests-holdingsitems-db \
                     update-systemtests-update-db \
                     update-systemtests-fake-smtp \
                     update-systemtests-fbs \
                     update-systemtests-dataio  || die "Docker-compose up -d failed"

sleep 10 || die "sleep failed"

echo "Wait for glassfish containers"
../../bin/return-when-status-ok.sh ${HOST_IP} 18180 '[dataio]'
../../bin/return-when-status-ok.sh ${HOST_IP} 18280 '[fbs]'

sleep 3 || die "sleep failed"

echo "Start and run systemtests"
docker-compose up ocb-tools-systemtests || echo "docker-compose up ocb-tools-systemtests failed"

echo "Collect log files"
docker logs systemtests_update-systemtests-rawrepo-db_1 > logs/pg-rawrepo.log
docker logs systemtests_update-systemtests-holdingsitems-db_1 > logs/pg-holdingsitems.log
docker logs systemtests_update-systemtests-update-db_1 > logs/pg-updatedb.log
docker logs systemtests_update-systemtests-fbs_1 > logs/gf-fbs.log
docker logs systemtests_update-systemtests-dataio_1 > logs/gf-dataio.log
docker logs systemtests_ocb-tools-systemtests_1 > logs/ocb-tools.log

sleep 3 || die "sleep failed"
echo "Stop glassfish containers"
docker-compose down || die "docker-compose down failed"