#!/usr/bin/env bash

./delete-results.sh
./build-image.sh
./run-image.sh $@
