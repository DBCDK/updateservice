<img src="http://www.dbc.dk/logo.png" alt="DBC" title="DBC" align="right">

# UpdateService

UpdaterService er en SOAP webservice som bruges til at validerer og indlægge poster i råpostrepositoriet.
Servicen er udviklet med Java EE og kræver en Java EE 7 container for at kunne afvikles. Servicen er blevet testet
med Payara 4.0.

### Developers how-to

You need to clone the following projects to the same root folder:
* updateservice (https://github.com/DBCDK/updateservice)
* opencat-business (https://github.com/DBCDK/opencat-business)
* ocb-tools (https://github.com/DBCDK/ocb-tools)

Start by doing a maven install on ocb-tools. In most cases you won't have to mess around with ocb-tools after this as you just need the compiled artifact.

Next you have to run transpile-templates in opencat-business. If you are running on a machine with the dbc-jsshell package install run bin/run-js-tests.sh. Otherwise use bin/run-js-tests-in-docker.sh (assuming you have docker installed).

Now you are ready to start working on updateservice.

Bootstrapping the environment (first ever run only):
* docker/bin/create_update_network.sh
* docker/bin/build-update-docker-parent.sh

Then start work by doing:
* docker/bin/start_dev_docker.sh

And then, after each change in the code:
* docker/bin/start-local-docker.sh

When you are done, remove your test containers by running:
* docker/bin/stop_dev_docker.sh

### Endpoint

Når servicen er deployet kan den tilgås via følgende:

* SOAP-operationer: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService
* WSDL: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService?wsdl

### System properties

Servicen bruger følgende properties:
* **UPDATE_LOGBACK_FILENAME**: Bruges af logback til at definere den fulde sti samt filnavn på log filen ekslusiv suffix. Kunne f.eks. være /home/thl/gf-logs/update

### JDBC Resources

* **jdbc/updateservice/raw-repo-readonly**: Readonly JDBC-resource til råpostrepositoriet. Det anbefaldes at anvende klassen *org.postgresql.ds.PGPoolingDataSource*.
* **jdbc/updateservice/raw-repo-writable**: Skrivbar JDBC-resource til råpostrepositoriet. Denne resource skal være
transaktionssikker, da den bruges til at ændre råpostrepositoriet. Det anbefaldes at anvende klassen *org.postgresql.xa.PGXADataSource*.
* **jdbc/updateservice/holdingitems**: JDBC-resource til Holding+ databasen. UpdateService læser kun fra denne database,
så resourcen behøver ikke være transaktionssikker. Det anbefaldes at anvende klassen *org.postgresql.ds.PGPoolingDataSource*.
* **jdbc/updateservice/updateservicestore**: JDBC-resource til dobbeltpostkontrol frontend nøgledatabase.
    * **Pool Name**: *jdbc/updateservice/updateservicestore/pool*

### JDBC Connection pools

* **jdbc/updateservice/updateservicestore/pool**: Denne forbindelse er sat op med følgende værdier:
    * **datasource-classname**: org.postgresql.ds.PGSimpleDataSource
    * **driverClass**: org.postgresql.Driver
    * **ServerName**: <addresse på postgres maskine>
    * **PortNumber**: <portnummer på postgres maskine>
    * **DatabaseName**: <databasenavn på postgresmaskine>
    * **User**: <brugernavn på database bruger>
    * **Password**: <password på database bruger>

### JNDI Resources

Servicen bruger disse resourcer:

* **update-log/logback**: Indeholder en url til logback configureringen. kunne f.eks. være file:///home/thl/gf-logs/update-logback-include.xml
* **updateservice/settings**: Custom resource med ekstra settings.

**updateservice/settings** skal være af typen *Properties* med følgende værdier:

* *solr.url*: Angiver den fulde url til SOLR-indekset inkl. core.
* *solr.basis.url*: Angiver den fulde url til Basis SOLR-indekset inkl. core.
* *forsrights.url*: Angiver den fulde url til forsrights webservicen. Pt er det vigtigt at huske at afslutte url'en
med "/" da forsrights har redirect (http kode 301) på url'en uden "/". Og det tilfælde kan updateservice ikke håndterer.
* *openagency.url*: Angiver den fulde url til openagency webservicen. Pt er det vigtigt at huske at afslutte url'en
                    med "/" da forsrights har redirect (http kode 301) på url'en uden "/". Og det tilfælde kan
                    updateservice ikke håndterer.
* *auth.product.name*: Angiver navnet på det produkt, som forsrights skal returnerer for at brugeren har adgang til
at bruge denne webservice.
* *auth.use.ip*: Angiver om klientens IP-adresse skal sendes videre til forsrights webservice ved authentikation af
brugere. Angives værdien *True* sendes IP-adressen med. Hvis settingen indeholder en anden værdi eller hvis den er
helt udeladt så sendes IP-adressen *ikke* videre til forsrights.
* *javascript.basedir*: Angiver rodkataloget hvor opencat-business distributionen ligger. Hvis man peger på sit projekt fra svn skal man
pege hvor https://svn.dbc.dk/repos/opencat-business/trunk/ ligger.
* *javascript.pool.size*: Heltal som angiver hvor mange Nashorn/Rhino engines som skal oprettes i det JavaScript Engine pool som Update anvender
 til JavaScript. Et udgangspunkt vil være intervallet mellem *thread-pool.size / 2* og *thread-pool.size*.
* *rawrepo.provider.id*: Angiver hvilket provide id som skal anvendes hvis det ikke er angivet i posten på requestet.
* *prod.state*: Angiver om update kører i produktion eller ej, og derfor om 13 biblioteker må sende poster ind. Kan være True/true eller False/false.

Eksempler på hvordan ovenstående ressourcer sættes op via asadmin fra kommandolinien:
asadmin set resources.custom-resource.updateservice/settings.property.javascript\\.basedir=/home/thl/NetBeansProjects/opencat-business/trunk
asadmin set resources.custom-resource.updateservice/settings.property.javascript\\.install\\.name=fbs

#### PostgreSQL

For at JDBC-forbindelserne virker med transaktioner skal featuren *prepared_transactions* være aktiveret i PostgreSQL.
Ellers vil transaktioner blive afbrudt når UpdateService forsøger at opdaterer en post i råpostrepositoriet.

### Logging

Til logning af diverse beskeder anvendes logback. Servicen 2 system properties til det:

* **LOGDIR**: Angiver den fulde path til den folder hvor logfilerne skal placeres.
* **logback.configurationFile**: Den fulde path til logback.xml konfigurationsfilen.
