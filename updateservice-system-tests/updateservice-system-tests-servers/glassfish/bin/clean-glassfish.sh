#!/bin/bash

BASEDIR=$(cd "$(dirname "$0")"; pwd)/..

# Cleanup home dir.
rm -rf $BASEDIR/home
