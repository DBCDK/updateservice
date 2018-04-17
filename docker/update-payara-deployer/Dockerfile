FROM docker-i.dbc.dk/update-payara:latest
MAINTAINER Team Iscrum, iscrum@dbc.dk

ARG BRANCH_NAME=master
ARG BUILD_NUMBER=lastSuccessfulBuild

ADD dbc-payara.d/* $PAYARA_USER_HOME/dbc-payara.d/
ADD https://is.dbc.dk/job/updateservice/job/updateservice-master/job/$BRANCH_NAME/$BUILD_NUMBER/artifact/target/opencat-business.tar.gz /data/
ADD https://is.dbc.dk/job/updateservice/job/updateservice-master/job/$BRANCH_NAME/$BUILD_NUMBER/artifact/target/updateservice-2.0-SNAPSHOT.war /data/

USER root
RUN chown -R $PAYARA_USER:$PAYARA_USER /data && \
    chown -R $PAYARA_USER:$PAYARA_USER $PAYARA_USER_HOME 

USER $PAYARA_USER

