#!/bin/bash
function die() {
    echo -e "Error:" "$@"
    exit 1
}

function sanityCheckInput(){
if [[ $# -ne 2 ]] ; then
    die  "Must be called with two parameters: project-name, tag"
fi
if [ ${USER} != "isworker" ]; then
    die "This program is only meant to run on an Jenkins node \nIf you want to run a local systest use the dev version"
fi
}
function buildNPush() {
	echo "----> tagging and pushing images $1:$2"
	TAG="docker-i.dbc.dk/$1:$2"
    cd docker/$1 																				|| die "Changing dir failed"
	docker build -t "$TAG" .\
    --label=svn="${SVN_REVISION}"\
    --label=user="${USER}"\
    --label=jobname="${JOB_NAME}"\
    --label=buildnumber="${BUILD_NUMBER}" 														|| die "Building "$TAG" failed"
	docker push "$TAG" 	 																		|| die "Pushing "$TAG" failed"
    cd - 																						|| die "Changing dir failed"
}
sanityCheckInput $1 $2
buildNPush $1 $2
