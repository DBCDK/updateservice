#!/bin/bash

curl -m 10 http://$1:$2/UpdateService/rest/status
