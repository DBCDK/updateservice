#!/bin/bash

BASEDIR=$(cd "$(dirname "$0")"; pwd)/..

# Delete logs directory.
rm -rf $BASEDIR/home/logs
