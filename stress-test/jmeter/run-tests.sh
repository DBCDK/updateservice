#!/usr/bin/env bash

docker run -i -t --rm -v $(pwd):/root --add-host="service-host:$(HOST_IP)" -w /root hauptmedia/jmeter /opt/jmeter/bin/jmeter -n -t StressPlan.jmx
