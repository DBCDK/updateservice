#!/usr/bin/env bash

RESULTS_DIR=results

if [ -d "$RESULTS_DIR" ]; then
    rm -rf $RESULTS_DIR
fi

mkdir $RESULTS_DIR
