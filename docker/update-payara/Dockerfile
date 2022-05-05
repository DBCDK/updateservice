FROM docker-dbc.artifacts.dbccloud.dk/payara5-micro:latest

LABEL RAWREPO_DB_URL="Full connection string for the rawrepo database. Format is 'username:pass@dbserver:port/dbname'. (Required)"
LABEL HOLDINGS_ITEMS_DB_URL="Full connection string for the holdings items database. Format is 'username:pass@dbserver:port/dbname'. (Required)"
LABEL UPDATE_DB_URL="Full connection string for the update database. Format is 'username:pass@dbserver:port/dbname'. (Required)"

RUN echo "set server-config.transaction-service.timeout-in-seconds=12000" >> scripts/prebootcommandfile.txt

ENV RAWREPO_PROVIDER_ID_DBC empty
ENV RAWREPO_PROVIDER_ID_FBS empty
ENV RAWREPO_PROVIDER_ID_PH empty
ENV RAWREPO_PROVIDER_ID_PH_HOLDINGS empty

ENV AUTH_PRODUCT_NAME empty
ENV AUTH_PRODUCT_RIGHT empty
ENV DBC_OVERWRITE_AGENCIES empty
ENV IDP_SERVICE_URL empty
ENV IDP_CACHE_AGE empty

ENV VIPCORE_ENDPOINT empty

ENV OPENNUMBERROLL_URL https://opennumberroll.addi.dk/1.1/
ENV OPENNUMBERROLL_NAME_FAUST_8 faust
ENV OPENNUMBERROLL_NAME_FAUST faust

ENV SOLR_URL empty
ENV SOLR_BASIS_URL empty

ENV OPENCAT_BUSINESS_URL empty

ENV UPDATE_PROD_STATE false

COPY app.json deployments/

LABEL INSTANCE_NAME="Name of the updateservice instance. Default is blank (Optional)"
LABEL MAINTAINER="meta-scrum@dbc.dk"
