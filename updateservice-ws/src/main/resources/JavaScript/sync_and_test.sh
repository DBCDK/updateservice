#!/bin/bash

rsync -av * stp@hejsa:/home/stp/projects/javascript/updateservice
ssh hejsa -X "cd projects/javascript/updateservice; ./run_tests.sh"
rsync -av stp@hejsa:/home/stp/projects/javascript/updateservice/*.log .
