#!/usr/bin/env bash

DBC_NETWORK_PREFIX='172.16'

export IP_ADDRESS=`ifconfig | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*' | grep -v '127.0.0.1' | grep ${DBC_NETWORK_PREFIX}`
