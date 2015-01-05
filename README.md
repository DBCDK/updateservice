UpdaterService
==============

UpdaterService er en SOAP webservice som bruges til at validerer og indlægge poster i råpostrepositoriet. 
Servicen er udviklet med Java EE og kræver en Java EE 7 container for at kunne afvikles. Servicen er blevet testet 
med Glassfish 4.0.

### Endpoint

Når servicen er deployet kan den tilgås via følgende:

* SOAP-operationer: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService
* WSDL: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService?wsdl

### Resourcer

Servicen bruger disse resourcer:

* **jdbc/updateservice/raw-repo-readonly**: Readonly JDBC-resource til råpostrepositoriet. Det anbefaldes at anvende klassen *org.postgresql.ds.PGPoolingDataSource*.
* **jdbc/updateservice/raw-repo-writable**: Skrivbar JDBC-resource til råpostrepositoriet. Denne resource skal være
transaktionssikker, da den bruges til at ændre råpostrepositoriet. Det anbefaldes at anvende klassen *org.postgresql.xa.PGXADataSource*.
* **jdbc/updateservice/holdingitems**: JDBC-resource til Holding+ databasen. UpdateService læser kun fra denne database,
så resourcen behøver ikke være transaktionssikker. Det anbefaldes at anvende klassen *org.postgresql.ds.PGPoolingDataSource*.
* **updateservice/settings**: Custom resource med ekstra settings.

**updateservice/settings** skal være af typen *Properties* med følgende værdier:

* *solr.url*: Angiver den fulde url til SOLR-indekset inkl. core.
* *forsrights.url*: Angiver den fulde url til forsrights webservicen. Pt er det vigtigt at huske at afslutte url'en 
med "/" da forsrights har redirect (http kode 301) på url'en uden "/". Og det tilfælde kan updateservice ikke håndterer.
* *auth.product.name*: Angiver navnet på det produkt, som forsrights skal returnerer for at brugeren har adgang til 
at bruge denne webservice. 
* *auth.use.ip*: Angiver om klientens IP-adresse skal sendes videre til forsrights webservice ved authentikation af 
brugere. Angives værdien *True* sendes IP-adressen med. Hvis settingen indeholder en anden værdi eller hvis den er 
helt udeladt så sendes IP-adressen *ikke* videre til forsrights.

#### PostgreSQL

For at JDBC-forbindelserne virker med transaktioner skal featuren *prepared_transactions* være aktiveret i PostgreSQL.
Ellers vil transaktioner blive afbrudt når UpdateService forsøger at opdaterer en post i råpostrepositoriet.

### Logging

Til logning af diverse beskeder anvendes logback. Servicen 2 system properties til det:

* **LOGDIR**: Angiver den fulde path til den folder hvor logfilerne skal placeres.
* **logback.configurationFile**: Den fulde path til logback.xml konfigurationsfilen.

  