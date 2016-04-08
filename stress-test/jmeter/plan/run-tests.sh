#!/usr/bin/env bash

unzip requests.zip
./generate_settings.sh
jmeter -n -t plan.jmx -p settings.properties -l results/jmeter.log
