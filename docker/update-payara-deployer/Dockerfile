FROM docker-i.dbc.dk/update-payara:latest
MAINTAINER Team Iscrum, iscrum@dbc.dk

ADD dbc-payara.d/* $PAYARA_USER_HOME/dbc-payara.d/
ADD http://is.dbc.dk/job/updateservice/job/opencat-business-head/lastSuccessfulBuild/artifact/deploy/opencat-business.tar.gz /data/
ADD http://is.dbc.dk/job/updateservice/job/updateservice-head/ws/target/updateservice-2.0-SNAPSHOT.war /data/

USER root
RUN chown -R $PAYARA_USER:$PAYARA_USER /data && \
    chown -R $PAYARA_USER:$PAYARA_USER $PAYARA_USER_HOME 

USER $PAYARA_USER

