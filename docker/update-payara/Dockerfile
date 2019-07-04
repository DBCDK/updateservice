FROM docker.dbc.dk/payara5-full:latest

LABEL RAWREPO_DB_URL="Full connection string for the rawrepo database. Format is 'username:pass@dbserver:port/dbname'. (Required)"
LABEL HOLDINGS_ITEMS_DB_URL="Full connection string for the holdings items database. Format is 'username:pass@dbserver:port/dbname'. (Required)"
LABEL UPDATE_DB_URL="Full connection string for the update database. Format is 'username:pass@dbserver:port/dbname'. (Required)"

ENV JAVASCRIPT_BASEDIR /data/opencat-business
ENV JAVASCRIPT_POOL_SIZE 1

ENV RAWREPO_PROVIDER_ID_DBC empty
ENV RAWREPO_PROVIDER_ID_FBS empty
ENV RAWREPO_PROVIDER_ID_PH empty
ENV RAWREPO_PROVIDER_ID_PH_HOLDINGS empty

ENV AUTH_PRODUCT_NAME netpunkt.dk
ENV AUTH_USE_IP True
ENV FORSRIGHTS_URL http://forsrights.addi.dk/1.2/

ENV OPENAGENCY_URL http://openagency.addi.dk/test_2.34/
ENV OPENAGENCY_CACHE_AGE 8

ENV OPENNUMBERROLL_URL http://opennumberroll.addi.dk/1.0/
ENV OPENNUMBERROLL_NAME_FAUST_8 faust
ENV OPENNUMBERROLL_NAME_FAUST faust

ENV SOLR_URL empty
ENV SOLR_BASIS_URL empty

ENV SMTP_PORT_25_TCP_ADDR empty
ENV SMTP_PORT_25_TCP_PORT empty
ENV SMTP_USER empty
ENV SMTP_PASSWORD empty
ENV SMTP_FROM empty
ENV SMTP_RECIPIENTS empty

ENV MAX_HEAP_IN_GB 1

ENV UPDATE_PROD_STATE false

COPY app.json deployments/

LABEL INSTANCE_NAME="Name of the updateservice instance. Default is blank (Optional)"
LABEL MAINTAINER="meta-scrum@dbc.dk"