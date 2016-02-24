UpdaterService
==============

UpdaterService er en SOAP webservice som bruges til at validerer og indlægge poster i råpostrepositoriet.
Servicen er udviklet med Java EE og kræver en Java EE 7 container for at kunne afvikles. Servicen er blevet testet
med Glassfish 4.0.

### Endpoint

Når servicen er deployet kan den tilgås via følgende:

* SOAP-operationer: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService
* WSDL: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService?wsdl

### System properties

Servicen bruger følgende properties:
* **UPDATE_LOGBACK_FILENAME**: Bruges af logback til at definere den fulde sti samt filnavn på log filen ekslusiv suffix. Kunne f.eks. være /home/thl/gf-logs/update

### JNDI Resourcer

Servicen bruger disse resourcer:

* **jdbc/updateservice/raw-repo-readonly**: Readonly JDBC-resource til råpostrepositoriet. Det anbefaldes at anvende klassen *org.postgresql.ds.PGPoolingDataSource*.
* **jdbc/updateservice/raw-repo-writable**: Skrivbar JDBC-resource til råpostrepositoriet. Denne resource skal være
transaktionssikker, da den bruges til at ændre råpostrepositoriet. Det anbefaldes at anvende klassen *org.postgresql.xa.PGXADataSource*.
* **jdbc/updateservice/holdingitems**: JDBC-resource til Holding+ databasen. UpdateService læser kun fra denne database,
så resourcen behøver ikke være transaktionssikker. Det anbefaldes at anvende klassen *org.postgresql.ds.PGPoolingDataSource*.
* **update-log/logback**: Indeholder en url til logback configureringen. kunne f.eks. være file:///home/thl/gf-logs/update-logback-include.xml
* **updateservice/settings**: Custom resource med ekstra settings.

**updateservice/settings** skal være af typen *Properties* med følgende værdier:

* *solr.url*: Angiver den fulde url til SOLR-indekset inkl. core.
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
* *javascript.install.name*: Navnet på den distribution fra https://svn.dbc.dk/repos/opencat-business/trunk/distributions/ man vil bruge.
* *allow.extra.record.data*: True/False. True angiver at et request må indeholde ekstra data for posten. Hvis
 settingen ikke er oprettet i Glassfish anvendes værdien "False".
* *rawrepo.provider.id*: Angiver hvilket provide id som skal anvendes hvis det ikke er angivet i posten på requestet.

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

