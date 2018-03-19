#!/usr/bin/env bash

cd update-postgres/
echo "--- update-postgres ---"
echo "- Building..."
docker build -t update-postgres .
echo "- Tagging..."
docker tag update-postgres docker-i.dbc.dk/update-postgres:latest
echo "- Pushing..."
docker push docker-i.dbc.dk/update-postgres:latest
echo "--- DONE ---"
cd -

cd update-glassfish
echo "--- update-glassfish ---"
echo "- Building..."
docker build -t update-glassfish .
echo "- Tagging..."
docker tag update-glassfish docker-i.dbc.dk/update-glassfish:latest
echo "- Pushing..."
docker push docker-i.dbc.dk/update-glassfish:latest
echo "--- DONE ---"
cd -

cd update-glassfish-deployer
echo "--- update-glassfish-deployer ---"
echo "- Building..."
docker build -t update-glassfish-deployer .
echo "- Tagging..."
docker tag update-glassfish-deployer docker-i.dbc.dk/update-glassfish-deployer:latest
echo "- Pushing..."
docker push docker-i.dbc.dk/update-glassfish-deployer:latest
echo "--- DONE ---"
cd -