#!/bin/bash

BASEDIR=$(cd "$(dirname "$0")"; pwd)/..
PG_HOME=$BASEDIR/home/pgsql

PG_HOST=localhost
PG_PORT=13132

DB_RAWREPO_NAME=rawrepo
DB_RAWREPO_SCHEMA_URL=https://svn.dbc.dk/repos/rawrepo/trunk/access/schema/rawrepo.sql
DB_RAWREPO_SCHEMA_FILE=$BASEDIR/home/rawrepo.sql
DB_RAWREPO_QUEUERULES_SCHEMA_URL=https://svn.dbc.dk/repos/rawrepo/trunk/access/schema/queuerules.sql
DB_RAWREPO_QUEUERULES_SCHEMA_FILE=$BASEDIR/home/$DB_RAWREPO_NAME-queuerules.sql

DB_HOLDINGSITEMS_NAME=holdingsitems
DB_HOLDINGSITEMS_SCHEMA_URL=https://svn.dbc.dk/repos/holdings-items/trunk/access/schema/holdingsitems.sql
DB_HOLDINGSITEMS_SCHEMA_FILE=$BASEDIR/home/holdingsitems.sql
DB_HOLDINGSITEMS_QUEUERULES_SCHEMA_URL=https://svn.dbc.dk/repos/holdings-items/trunk/access/schema/queuerules.sql
DB_HOLDINGSITEMS_QUEUERULES_SCHEMA_FILE=$BASEDIR/home/$DB_HOLDINGSITEMS_NAME-queuerules.sql

die() { echo "$@" 1>&2 ; exit 1; }

# Cleanup home dir.
rm -rf $BASEDIR/home

# Unpack Postgresql distribution
if [ "$1" == "mac" ]; then
	unzip $BASEDIR/postgresql-9.3.5-1-osx-binaries.zip -d $BASEDIR/home
elif [ "$1" == "linux" ]; then
	die "Platform '$1' is not implementated yet!"
else
	die "Platform '$1' is not supported"
fi

# Setup preinstalled domain "domain1"
echo "Try to setup postgresql"
$PG_HOME/bin/initdb -D $BASEDIR/home/postgresql-cluster -A trust --locale=da_DK.UTF-8

echo "Try to start postgresql"
$PG_HOME/bin/pg_ctl start -w -D $BASEDIR/home/postgresql-cluster -l $BASEDIR/home/postgresql.log -o "-h $PG_HOST -p $PG_PORT"  

echo "Try to create database for rawrepo"
wget $DB_RAWREPO_SCHEMA_URL -O $DB_RAWREPO_SCHEMA_FILE
wget $DB_RAWREPO_QUEUERULES_SCHEMA_URL -O $DB_RAWREPO_QUEUERULES_SCHEMA_FILE
$PG_HOME/bin/createdb -h $PG_HOST -p $PG_PORT $DB_RAWREPO_NAME
$PG_HOME/bin/psql -h $PG_HOST -p $PG_PORT --dbname=$DB_RAWREPO_NAME < $DB_RAWREPO_SCHEMA_FILE
$PG_HOME/bin/psql -h $PG_HOST -p $PG_PORT --dbname=$DB_RAWREPO_NAME < $DB_RAWREPO_QUEUERULES_SCHEMA_FILE

echo "Try to create database for holdingsitems"
wget $DB_HOLDINGSITEMS_SCHEMA_URL -O $DB_HOLDINGSITEMS_SCHEMA_FILE
wget $DB_HOLDINGSITEMS_QUEUERULES_SCHEMA_URL -O $DB_HOLDINGSITEMS_QUEUERULES_SCHEMA_FILE
$PG_HOME/bin/createdb -h $PG_HOST -p $PG_PORT $DB_HOLDINGSITEMS_NAME
$PG_HOME/bin/psql -h $PG_HOST -p $PG_PORT --dbname=$DB_HOLDINGSITEMS_NAME < $DB_HOLDINGSITEMS_SCHEMA_FILE
$PG_HOME/bin/psql -h $PG_HOST -p $PG_PORT --dbname=$DB_HOLDINGSITEMS_NAME < $DB_HOLDINGSITEMS_QUEUERULES_SCHEMA_FILE
