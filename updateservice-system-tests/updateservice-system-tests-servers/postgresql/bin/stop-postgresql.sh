#!/bin/bash

BASEDIR=$(cd "$(dirname "$0")"; pwd)/..
PG_HOME=$BASEDIR/home/pgsql

echo "Try to stop postgresql"
$PG_HOME/bin/pg_ctl stop -D $BASEDIR/home/postgresql-cluster -m immediate   
