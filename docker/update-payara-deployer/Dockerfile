ARG PARENT_IMAGE=docker-io.dbc.dk/update-payara:latest
FROM $PARENT_IMAGE

ARG BRANCH_NAME=master
ARG BUILD_NUMBER=lastSuccessfulBuild

RUN echo $BRANCH_NAME

USER root

RUN cd deployments && \
    curl -o "updateservice-2.0-SNAPSHOT.war" https://is.dbc.dk/job/updateservice/job/updateservice-master/job/$BRANCH_NAME/$BUILD_NUMBER/artifact/target/updateservice-2.0-SNAPSHOT.war && \
    curl -o "opencat-business.tar.gz" https://is.dbc.dk/job/updateservice/job/opencat-business/job/master/lastSuccessfulBuild/artifact/deploy/opencat-business.tar.gz && \
    tar zxvf opencat-business.tar.gz && \
    rm opencat-business.tar.gz && \
    cd -

RUN chown -R $PAYARA_USER:$PAYARA_USER deployments

USER $PAYARA_USER

LABEL MAINTAINER="meta-scrum@dbc.dk"