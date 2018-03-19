#!/bin/bash

#
# Only meant to be used from local PC not the IS
#

function die() {
    echo "ERROR:" "$@"
    docker logout docker-i.dbc.dk
    exit 1
}

if [ $# -ne 1 ] ; then
    die "Must be called with a single docker image name, i.e.: ${0} updateservice-db-postgres" 
fi

echo "Login to artifactory with user ${USER} (windows domain login)"
read -s -p "Enter artifactory password: " PASSWORD
docker login -u ${USER} -p ${PASSWORD} docker-i.dbc.dk || die "docker login -u ${USER} --email iscrum@dbc.dk docker-i.dbc.dk"
echo "Tagging image: ${1}"
docker tag ${1} docker-i.dbc.dk/${1}:latest || die "docker tag ${1} docker-i.dbc.dk/${1}:latest"
echo "Pushing image to artifacktory (docker-i.dbc.dk)"
docker push docker-i.dbc.dk/${1} || die "docker push docker-i.dbc.dk/${1}"
echo "Logging out of artifactory"
docker logout docker-i.dbc.dk || die "docker logout docker-i.dbc.dk"
