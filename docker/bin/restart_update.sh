#!/bin/bash
#set -x
USER=${USER:-"unknown"}    # silencing annoying intellij quibble
export IDEA_ROOT=$(dirname $(dirname $(dirname $(realpath ${0}))))

cd ${IDEA_ROOT}
mvn verify install -Dmaven.test.skip=true
bash ${IDEA_ROOT}/docker/bin/undeploy-app.sh ${USER}_updateservice_1 ${IDEA_ROOT}/target/updateservice-2.0-SNAPSHOT.war
bash ${IDEA_ROOT}/docker/bin/deploy-app.sh ${USER}_updateservice_1 ${IDEA_ROOT}/target/updateservice-2.0-SNAPSHOT.war

server=`grep updateservice.url ${HOME}/.ocb-tools/testrun.properties | cut -d"=" -f2 | tr -d " "`
echo "Waiting for ready signal"
RES=ST_NA
let count=0
while [ "${RES}" = "ST_NA" ]
do
    let count++
    echo -n -e "\r$count"
    RES=`curl -s -m 10 ${server}/UpdateService/rest/status`
    sleep 1
done

