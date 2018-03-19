#!/usr/bin/env bash

LOG_CONFIG_DIR=../update-glassfish
LOG_DIR=../logs

if [ -d "$LOG_DIR" ]; then
    rm -rf $LOG_DIR
fi

mkdir -p $LOG_DIR/update/app
mkdir -p $LOG_DIR/update/server
mkdir -p $LOG_DIR/fakesmtp

touch $LOG_DIR/update/server/server.log

cp $LOG_CONFIG_DIR/*logback*.xml $LOG_DIR/update/app/.
