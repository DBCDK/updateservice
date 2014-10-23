UpdaterService
==============

UpdaterService er en SOAP webservice som bruges til at validerer og indlægge poster i råpostrepositoriet. Servicen er udviklet med Java EE
og kræver en Java EE 7 container for at kunne afvikles. Servicen er blevet testet med Glassfish 4.0.

### Endpoint

Når servicen er deployet kan den tilgås via følgende:

* SOAP-operationer: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService
* WSDL: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService?wsdl

### Resourcer

Servicen bruger disse resourcer:

* **jdbc/updateservice/raw-repo**: JDBC-resource til råpostrepositoriet. 
* **jdbc/updateservice/holdingitems**: JDBC-resource til Holding+ databasen.
* **updateservice/settings**: Custom resource med ekstra settings.

**updateservice/settings** skal være af typen *Properties* med følgende værdier:

* *solr.url*: Angiver den fulde url til SOLR-indekset inkl. core.
* *forsrights.url*: Angiver den fulde url til forsrights webservicen.

### Logging

Til logning af diverse beskeder anvendes logback. Servicen 2 system properties til det:

* **LOGDIR**: Angiver den fulde path til den folder hvor logfilerne skal placeres.
* **logback.configurationFile**: Den fulde path til logback.xml konfigurationsfilen.

  