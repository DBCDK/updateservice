ARG PARENT_IMAGE=docker-i.dbc.dk/update-payara:latest
FROM $PARENT_IMAGE

ARG BRANCH_NAME=master
ARG BUILD_NUMBER=lastSuccessfulBuild

RUN echo $BRANCH_NAME

ADD dbc-payara.d/* $PAYARA_USER_HOME/dbc-payara.d/
ADD https://is.dbc.dk/job/updateservice/job/opencat-business/job/master/lastSuccessfulBuild/artifact/deploy/opencat-business.tar.gz /data/
ADD https://is.dbc.dk/job/updateservice/job/updateservice-master/job/marc21_proof_of_concept/lastSuccessfulBuild/artifact/target/updateservice-2.0-SNAPSHOT.war /data/

USER root

RUN  apt-get update \
  && apt-get install -y wget \
  && apt-get clean

RUN cd /data && \
    wget https://is.dbc.dk/job/updateservice/job/updateservice-master/job/$BRANCH_NAME/$BUILD_NUMBER/artifact/target/updateservice-2.0-SNAPSHOT.war && \
    wget https://is.dbc.dk/job/updateservice/job/opencat-business/job/master/lastSuccessfulBuild/artifact/deploy/opencat-business.tar.gz && \
    tar zxvf opencat-business.tar.gz && \
    rm opencat-business.tar.gz && \
    cd -

RUN chown -R $PAYARA_USER:$PAYARA_USER /data && \
    chown -R $PAYARA_USER:$PAYARA_USER $PAYARA_USER_HOME 

USER $PAYARA_USER

LABEL MAINTAINER="meta-scrum@dbc.dk"