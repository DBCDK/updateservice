#!/bin/bash

function die() {
    echo "ERROR:" "$@"
    exit 1
}

if [[ $# -ne 3 ]] ; then
    echo "Must be called with three parameters: base-image current-tag new-tag"
    echo "Example: $0 ors-gateway latest staging"
    echo "This will create an ors-gateway image retagged as staging from latest"
    exit 1
fi

curl --user isworker:n0t_s0_secret -H 'accept:application/vnd.docker.distribution.manifest.v2+json' https://artifactory.dbc.dk/artifactory/api/docker/docker-i/v2/${1}/manifests/${2} > manifest.json || die "time curl --user ***:*** -H 'accept:application/vnd.docker.distribution.manifest.v2+json' https://artifactory.dbc.dk/artifactory/api/docker/docker-i/v2/${1}/manifests/${2} > manifest.json"
curl -X PUT --user isworker:n0t_s0_secret -H 'content-type:application/vnd.docker.distribution.manifest.v2+json' -d @manifest.json https://artifactory.dbc.dk/artifactory/api/docker/docker-i/v2/${1}/manifests/${3} || die "time curl -X PUT --user ***:*** -H 'content-type:application/vnd.docker.distribution.manifest.v2+json' -d @manifest.json https://artifactory.dbc.dk/artifactory/api/docker/docker-i/v2/${1}/manifests/${3}"
