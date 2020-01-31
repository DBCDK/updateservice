#!/usr/bin/env bash
#
# This script are only needed to be run once on a particular machine.
# theory says that the network will survive a reboot
#
docker network create --subnet=192.168.42.1/24 update-compose-network