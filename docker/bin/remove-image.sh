#!/usr/bin/env bash

[ -z $(docker images -q $1) ] || docker rmi $1