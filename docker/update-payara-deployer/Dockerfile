ARG PARENT_IMAGE=docker-i.dbc.dk/update-payara:latest
FROM $PARENT_IMAGE
MAINTAINER Team Metascrum, meta-scrum@dbc.dk

ARG BRANCH_NAME=master
ARG BUILD_NUMBER=lastSuccessfulBuild

RUN echo $BRANCH_NAME

ADD dbc-payara.d/* $PAYARA_USER_HOME/dbc-payara.d/
ADD opencat-business.tar.gz /data/
ADD updateservice-2.0-SNAPSHOT.war /data/

USER root
RUN chown -R $PAYARA_USER:$PAYARA_USER /data && \
    chown -R $PAYARA_USER:$PAYARA_USER $PAYARA_USER_HOME 

USER $PAYARA_USER

