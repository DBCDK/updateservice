FROM docker-io.dbc.dk/dbc-payara-logback:latest

MAINTAINER Team Iscrum, iscrum@dbc.dk

ENV SETTINGS_JAVASCRIPT_BASEDIR /data/opencat-business
ENV THREAD_POOL_SIZE 1
ENV SETTINGS_RAWREPO_PROVIDER_ID_DBC empty
ENV SETTINGS_RAWREPO_PROVIDER_ID_DBC_SOLR empty
ENV SETTINGS_RAWREPO_PROVIDER_ID_FBS empty
ENV SETTINGS_RAWREPO_PROVIDER_ID_PH empty
ENV SETTINGS_RAWREPO_PROVIDER_ID_PH_HOLDINGS empty
ENV SETTINGS_RAWREPO_ALLOW_EXTRA_RECORD_DATA False
ENV SETTINGS_AUTH_PRODUCT_NAME netpunkt.dk
ENV SETTINGS_AUTH_USE_IP True
ENV SETTINGS_FORSRIGHTS_URL http://forsrights.addi.dk/1.2/
ENV SETTINGS_OPENAGENCY_URL http://openagency.addi.dk/test_2.33/
ENV SETTINGS_OPENAGENCY_CACHE_AGE 8
ENV SETTINGS_OPENNUMBERROLL_URL http://opennumberroll.addi.dk/1.0/
ENV SETTINGS_OPENNUMBERROLL_NAME_FAUST_8 faust_8_test
ENV SETTINGS_OPENNUMBERROLL_NAME_FAUST faust_test

ENV SETTINGS_SMTP_USER empty
ENV SETTINGS_SMTP_PASSWORD empty
ENV SETTINGS_SMTP_FROM empty
ENV SETTINGS_SMTP_RECIPIENTS empty

ENV RAWREPO_DBNAME empty
ENV RAWREPO_USER empty
ENV RAWREPO_PASSWORD empty

ENV HOLDINGS_ITEMS_DBNAME empty
ENV HOLDINGS_ITEMS_USER empty
ENV HOLDINGS_ITEMS_PASSWORD empty
ENV MAX_HEAP_IN_GB 1

ADD dbc-payara.d $PAYARA_USER_HOME/dbc-payara.d/

ADD update-logback-include.xml /data/
ADD update-logback-include-stdout.xml /data/
ADD http://glu-repos.dbc.dk/meta/updateservice-logback-json-debug.xml /data/
ADD http://glu-repos.dbc.dk/meta/updateservice-logback-json-info.xml /data/

USER root
RUN mkdir -p /home/$PAYARA_USER && \ 
    chown -R $PAYARA_USER:$PAYARA_USER /data && \
    chown -R $PAYARA_USER:$PAYARA_USER $PAYARA_HOME && \
    chown -R $PAYARA_USER:$PAYARA_USER /home/$PAYARA_USER && \
    chmod +x $PAYARA_USER_HOME/dbc-payara.d/*.sh
USER $PAYARA_USER 

LABEL INSTANCE_NAME="Name of the updateservice instance. Default is blank (Optional)"