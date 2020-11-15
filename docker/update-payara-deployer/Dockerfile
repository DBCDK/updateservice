ARG PARENT_IMAGE=docker-io.dbc.dk/update-payara:latest
FROM $PARENT_IMAGE

ARG BRANCH_NAME=master
ARG BUILD_NUMBER=lastSuccessfulBuild

RUN echo $BRANCH_NAME

USER root

RUN cd deployments && \
    curl -o "updateservice-2.0-SNAPSHOT.war" https://is.dbc.dk/job/updateservice/job/updateservice-master/job/$BRANCH_NAME/$BUILD_NUMBER/artifact/target/updateservice-2.0-SNAPSHOT.war && \
    cd -

RUN chown -R $PAYARA_USER:$PAYARA_USER deployments

USER $PAYARA_USER

LABEL MAINTAINER="meta-scrum@dbc.dk"
