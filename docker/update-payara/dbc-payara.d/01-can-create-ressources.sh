#!/bin/bash

set -e

env

SETTINGS_OPENNUMBERROLL_URL_FORMAL=$(echo "${SETTINGS_OPENNUMBERROLL_URL}"|sed -e's/&amp;/&/g'|sed -e's/&/&amp;/g')
cat << EOF > ${PAYARA_USER_HOME}/dbc-payara.d/18-updateservice-settings.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE resources PUBLIC "-//GlassFish.org//DTD GlassFish Application Server 3.1 Resource Definitions//EN" "http://glassfish.org/dtds/glassfish-resources_1_5.dtd">
<resources>

	 <custom-resource factory-class="org.glassfish.resources.custom.factory.PropertiesFactory" res-type="java.util.Properties" jndi-name="updateservice/settings">
        <property name="javascript.basedir" value="${SETTINGS_JAVASCRIPT_BASEDIR}"></property>
        <property name="javascript.pool.size" value="${THREAD_POOL_SIZE}"></property>
        <property name="rawrepo.provider.id.dbc" value="${SETTINGS_RAWREPO_PROVIDER_ID_DBC}"></property>
        <property name="rawrepo.provider.id.dbc.solr" value="${SETTINGS_RAWREPO_PROVIDER_ID_DBC_SOLR}"></property>
        <property name="rawrepo.provider.id.fbs" value="${SETTINGS_RAWREPO_PROVIDER_ID_FBS}"></property>
        <property name="rawrepo.provider.id.ph" value="${SETTINGS_RAWREPO_PROVIDER_ID_PH}"></property>
        <property name="rawrepo.provider.id.ph.holdings" value="${SETTINGS_RAWREPO_PROVIDER_ID_PH_HOLDINGS}"></property>
		<property name="auth.product.name" value="${SETTINGS_AUTH_PRODUCT_NAME}"></property>
        <property name="auth.use.ip" value="${SETTINGS_AUTH_USE_IP}"></property>
        <property name="forsrights.url" value="${SETTINGS_FORSRIGHTS_URL}"></property>
        <property name="openagency.url" value="${SETTINGS_OPENAGENCY_URL}"></property>
        <property name="openagency.cache.age" value="${SETTINGS_OPENAGENCY_CACHE_AGE}"></property>
        <property name="solr.url" value="http://${SOLR_PORT_8080_TCP_ADDR}:${SOLR_PORT_8080_TCP_PORT}/${SOLR_PATH}"></property>
        <property name="opennumberroll.url" value="${SETTINGS_OPENNUMBERROLL_URL_FORMAL}"></property>
        <property name="opennumberroll.name.faust8" value="${SETTINGS_OPENNUMBERROLL_NAME_FAUST_8}"></property>
        <property name="opennumberroll.name.faust" value="${SETTINGS_OPENNUMBERROLL_NAME_FAUST}"></property>
        <property name="double.record.mail.host" value="${SMTP_PORT_25_TCP_ADDR}"></property>
        <property name="double.record.mail.port" value="${SMTP_PORT_25_TCP_PORT}"></property>
        <property name="double.record.mail.user" value="${SETTINGS_SMTP_USER}"></property>
        <property name="double.record.mail.password" value="${SETTINGS_SMTP_PASSWORD}"></property>
        <property name="double.record.mail.from" value="${SETTINGS_SMTP_FROM}"></property>
        <property name="double.record.mail.recipients" value="${SETTINGS_SMTP_RECIPIENTS}"></property>
        <property name="prod.state" value="${SETTINGS_PROD_STATE}"></property>
    </custom-resource>

</resources>
EOF


cat << EOF > ${PAYARA_USER_HOME}/dbc-payara.d/19-db-connection-pools.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE resources PUBLIC "-//GlassFish.org//DTD GlassFish Application Server 3.1 Resource Definitions//EN" "http://glassfish.org/dtds/glassfish-resources_1_5.dtd">

  <resources>
    <jdbc-connection-pool datasource-classname="org.postgresql.ds.PGSimpleDataSource"
                          connection-leak-reclaim="true"
                          connection-leak-timeout-in-seconds="600"
                          validate-atmost-once-period-in-seconds="10"
                          is-connection-validation-required="true" 
                          connection-validation-method="custom-validation"
                          validation-classname="org.glassfish.api.jdbc.validation.PostgresConnectionValidation"
                          fail-all-connections="true"
                          name="jdbc/updateservice/raw-repo/pool" res-type="javax.sql.DataSource">
        <property name="driverClass" value="org.postgresql.Driver"></property>
        <property name="ServerName" value="${RAWREPO_PORT_5432_TCP_ADDR}"></property>
        <property name="PortNumber" value="${RAWREPO_PORT_5432_TCP_PORT}"></property>
        <property name="DatabaseName" value="${RAWREPO_DBNAME}"></property>
        <property name="User" value="${RAWREPO_USER}"></property>
        <property name="Password" value="${RAWREPO_PASSWORD}"></property>
        <property name="BinaryTransfer" value="true"></property>
        <property name="Ssl" value="false"></property>
        <property name="ProtocolVersion" value="0"></property>
        <property name="TcpKeepAlive" value="false"></property>
        <property name="SocketTimeout" value="0"></property>
        <property name="LoginTimeout" value="0"></property>
        <property name="UnknownLength" value="2147483647"></property>
        <property name="PrepareThreshold" value="5"></property>
    </jdbc-connection-pool>
    <jdbc-resource pool-name="jdbc/updateservice/raw-repo/pool" jndi-name="jdbc/updateservice/raw-repo-readonly"></jdbc-resource>
    <jdbc-resource pool-name="jdbc/updateservice/raw-repo/pool" jndi-name="jdbc/updateservice/raw-repo-writable"></jdbc-resource>

    <jdbc-connection-pool datasource-classname="org.postgresql.ds.PGPoolingDataSource"
                          connection-leak-reclaim="true"
                          connection-leak-timeout-in-seconds="600"
                          validate-atmost-once-period-in-seconds="10"
                          is-connection-validation-required="true" 
                          connection-validation-method="custom-validation"
                          validation-classname="org.glassfish.api.jdbc.validation.PostgresConnectionValidation"
                          fail-all-connections="true"
                          name="jdbc/updateservice/holdingitems/pool" res-type="javax.sql.DataSource">
        <property name="driverClass" value="org.postgresql.Driver"></property>
        <property name="ServerName" value="${HOLDINGSITEMS_PORT_5432_TCP_ADDR}"></property>
        <property name="PortNumber" value="${HOLDINGSITEMS_PORT_5432_TCP_PORT}"></property>
        <property name="DatabaseName" value="${HOLDINGS_ITEMS_DBNAME}"></property>
        <property name="User" value="${HOLDINGS_ITEMS_USER}"></property>
        <property name="Password" value="${HOLDINGS_ITEMS_PASSWORD}"></property>
        <property name="BinaryTransfer" value="true"></property>
        <property name="Ssl" value="false"></property>
        <property name="ProtocolVersion" value="0"></property>
        <property name="TcpKeepAlive" value="false"></property>
        <property name="SocketTimeout" value="0"></property>
        <property name="LoginTimeout" value="0"></property>
        <property name="UnknownLength" value="2147483647"></property>
        <property name="PrepareThreshold" value="5"></property>
    </jdbc-connection-pool>
    <jdbc-resource pool-name="jdbc/updateservice/holdingitems/pool" jndi-name="jdbc/updateservice/holdingitems"></jdbc-resource>

    <jdbc-connection-pool datasource-classname="org.postgresql.ds.PGSimpleDataSource"
                          connection-leak-reclaim="true"
                          connection-leak-timeout-in-seconds="600"
                          validate-atmost-once-period-in-seconds="10"
                          is-connection-validation-required="true" 
                          connection-validation-method="custom-validation"
                          validation-classname="org.glassfish.api.jdbc.validation.PostgresConnectionValidation"
                          fail-all-connections="true"
                          name="jdbc/updateservice/updateservicestore/pool" res-type="javax.sql.DataSource">
        <property name="driverClass" value="org.postgresql.Driver"></property>
        <property name="ServerName" value="${UPDATE_DB_PORT_5432_TCP_ADDR}"></property>
        <property name="PortNumber" value="${UPDATE_DB_PORT_5432_TCP_PORT}"></property>
        <property name="DatabaseName" value="${UPDATE_STORE_DB}"></property>
        <property name="User" value="${UPDATE_STORE_USER}"></property>
        <property name="Password" value="${UPDATE_STORE_PASSWORD}"></property>
    </jdbc-connection-pool>
    <jdbc-resource pool-name="jdbc/updateservice/updateservicestore/pool" jndi-name="jdbc/updateservice/updateservicestore"></jdbc-resource>

</resources>
EOF

cat << EOF > ${PAYARA_USER_HOME}/dbc-payara.d/20-updateservice-settings.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE resources PUBLIC "-//GlassFish.org//DTD GlassFish Application Server 3.1 Resource Definitions//EN" "http://glassfish.org/dtds/glassfish-resources_1_5.dtd">
<resources>
	 <custom-resource factory-class="org.glassfish.resources.custom.factory.PrimitivesAndStringFactory" res-type="java.lang.String" jndi-name="update-log/logback">
        <property name="value" value="${LOGBACK_FILE}"></property>
    </custom-resource>
</resources>
EOF

SETTINGS_OPENNUMBERROLL_URL_FORMAL=$(echo "${SETTINGS_OPENNUMBERROLL_URL}"|sed -e's/&amp;/&/g'|sed -e's/&/&amp;/g')
cat << EOF > ${PAYARA_USER_HOME}/dbc-payara.d/21-buildservice-settings.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE resources PUBLIC "-//GlassFish.org//DTD GlassFish Application Server 3.1 Resource Definitions//EN" "http://glassfish.org/dtds/glassfish-resources_1_5.dtd">
<resources>

	 <custom-resource factory-class="org.glassfish.resources.custom.factory.PropertiesFactory" res-type="java.util.Properties" jndi-name="env/iscrum/build/properties">
        <property name="javascript.basedir" value="${SETTINGS_JAVASCRIPT_BASEDIR}"></property>
        <property name="javascript.pool.size" value="${THREAD_POOL_SIZE}"></property>
        <property name="forsrightsproduct" value="${SETTINGS_AUTH_PRODUCT_NAME}"></property>
        <property name="forsrightsurl" value="${SETTINGS_FORSRIGHTS_URL}"></property>
        <property name="opennumberroll.url" value="${SETTINGS_OPENNUMBERROLL_URL_FORMAL}"></property>
        <property name="opennumberroll.name.faust8" value="${SETTINGS_OPENNUMBERROLL_NAME_FAUST_8}"></property>
        <property name="opennumberroll.name.faust" value="${SETTINGS_OPENNUMBERROLL_NAME_FAUST}"></property>
    </custom-resource>
	 <custom-resource factory-class="org.glassfish.resources.custom.factory.PrimitivesAndStringFactory" res-type="java.lang.String" jndi-name="build-log/logback">
        <property name="value" value="${LOGBACK_FILE}"></property>
    </custom-resource>

</resources>
EOF

