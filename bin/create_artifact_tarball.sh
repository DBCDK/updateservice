#!/bin/bash -x

function die() {
  echo "ERROR: $@ failed"
  exit 1
}

rm -rf target/opencat-business || die "rm -rf target/opencat-business"

mkdir -p target/opencat-business || die "mkdir -p target/opencat-business"
cp -r bin target/opencat-business/ || die "cp -r bin target/opencat-business/"
cp -r distributions target/opencat-business/ || die "cp -r distributions target/opencat-business/"

if [ -n "${1}" ];
    then
        echo "opencat-business revision : ${1}" > target/opencat-business/svn_revision.txt;
    else
        echo "git revision could not be resolved" > target/opencat-business/svn_revision.txt;
fi

cd target || die "cd target"
tar --exclude-vcs --exclude=.idea -czf opencat-business.tar.gz opencat-business || die "tar --exclude-vcs --exclude=.idea -czf opencat-business.tar.gz opencat-business"
