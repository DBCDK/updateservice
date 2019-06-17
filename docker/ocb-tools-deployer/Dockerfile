FROM docker.dbc.dk/dbc-java8:latest

ENV OCB_USER ocbtools
ENV OCB_USER_HOME /home/$OCB_USER
RUN useradd $OCB_USER && \
    mkdir -p $OCB_USER_HOME/bin

ENV LOG_DIR /data/logs
RUN mkdir -p ${LOG_DIR}
RUN mkdir -p $OCB_USER_HOME/results

RUN mkdir -p /opencat-business/target/surefire-reports
ENV PATH $PATH:$OCB_USER_HOME/bin

ARG BRANCH_NAME=master
ARG BUILD_NUMBER=lastSuccessfulBuild

RUN  apt-get update \
  && apt-get install -y wget ca-certificates \
  && apt-get clean

RUN cd $OCB_USER_HOME && \
    wget -q https://is.dbc.dk/job/updateservice/job/ocb-tools/job/master/lastSuccessfulBuild/artifact/target/dist/ocb-tools-1.0.0.tar.gz && \
    tar zxf ocb-tools-1.0.0.tar.gz && \
    rm ocb-tools-1.0.0.tar.gz

RUN cd $OCB_USER_HOME && \
    wget -q https://is.dbc.dk/job/updateservice/job/opencat-business/job/master/lastSuccessfulBuild/artifact/deploy/opencat-business.tar.gz && \
    tar zxf opencat-business.tar.gz && \
    rm opencat-business.tar.gz

ADD scripts/bin/*.sh $OCB_USER_HOME/bin/

RUN cd $OCB_USER_HOME/bin && \
    ln -s ../target/dist/ocb-tools-1.0.0/bin/ocb-test.sh . && \
    chmod +x $OCB_USER_HOME/bin/*.sh && \
    chown -R $OCB_USER:$OCB_USER $OCB_USER_HOME && \
    chown -R $OCB_USER:$OCB_USER $LOG_DIR

USER $OCB_USER
EXPOSE 8080

LABEL MAINTAINER="meta-scrum@dbc.dk"