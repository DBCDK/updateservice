FROM docker.dbc.dk/payara5-full:latest

LABEL RAWREPO_DB_URL="Full connection string for the rawrepo database. Format is 'username:pass@dbserver:port/dbname'. (Required)"
LABEL HOLDINGS_ITEMS_DB_URL="Full connection string for the holdings items database. Format is 'username:pass@dbserver:port/dbname'. (Required)"
LABEL UPDATE_DB_URL="Full connection string for the update database. Format is 'username:pass@dbserver:port/dbname'. (Required)"

ENV JAVASCRIPT_BASEDIR /opt/payara5/deployments/opencat-business
ENV JAVASCRIPT_POOL_SIZE 1

ENV RAWREPO_PROVIDER_ID_DBC empty
ENV RAWREPO_PROVIDER_ID_FBS empty
ENV RAWREPO_PROVIDER_ID_PH empty
ENV RAWREPO_PROVIDER_ID_PH_HOLDINGS empty

ENV AUTH_PRODUCT_NAME netpunkt.dk
ENV AUTH_USE_IP True
ENV FORSRIGHTS_URL https://forsrights.addi.dk/2.0/

ENV OPENAGENCY_URL http://openagency.addi.dk/test_2.34/
ENV OPENAGENCY_CACHE_AGE 8

ENV OPENNUMBERROLL_URL https://opennumberroll.addi.dk/1.1/
ENV OPENNUMBERROLL_NAME_FAUST_8 faust
ENV OPENNUMBERROLL_NAME_FAUST faust

ENV SOLR_URL empty
ENV SOLR_BASIS_URL empty

ENV DOUBLE_RECORD_MAIL_HOST empty
ENV DOUBLE_RECORD_MAIL_PORT empty
ENV DOUBLE_RECORD_MAIL_USER empty
ENV DOUBLE_RECORD_MAIL_PASSWORD empty
ENV DOUBLE_RECORD_MAIL_FROM empty
ENV DOUBLE_RECORD_MAIL_RECIPIENT empty

ENV UPDATE_PROD_STATE false

COPY app.json deployments/

LABEL INSTANCE_NAME="Name of the updateservice instance. Default is blank (Optional)"
LABEL MAINTAINER="meta-scrum@dbc.dk"