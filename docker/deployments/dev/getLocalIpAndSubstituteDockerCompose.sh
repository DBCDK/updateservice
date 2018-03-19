#!/usr/bin/env bash

if [ -z "$1" ];
    then
    echo  "ERROR : first argument need to be the name of the preffered network interface"
    exit -1
fi

if [ -z "$2" ];
    then
    echo  "ERROR : second argument must be the path to the dockerfile in which you want to substitute ip"
    exit -1
 fi

NETWORK_INTERFACES=$1
DOCKER_COMPOSE_YML=$2

IP_ADD=`ifconfig $NETWORK_INTERFACES | sed -En 's/127.0.0.1//;s/.*inet (addr:)?(([0-9]*\.){3}[0-9]*).*/\2/p'`
echo "substituting ip ${IP_ADD} with ip in ${DOCKER_COMPOSE_YML}"
sed -Ei "s/\"solrserver\:[^\"]*\"/\"solrserver\:${IP_ADD}\"/g" ${DOCKER_COMPOSE_YML}


