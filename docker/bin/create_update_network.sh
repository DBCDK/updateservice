#!/usr/bin/env bash
#
# This script are only needed to be run once on a particular machine.
# theory says that the network will survive a reboot
#
docker network create --subnet=192.180.0.0/22 update-compose-network