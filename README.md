UpdaterService
==============

UpdaterService er en SOAP webservice som bruges til at validerer og indlægge poster i råpostrepositoriet. Servicen er udviklet med Java EE
og kræver en Java EE 7 container for at kunne afvikles. Servicen er blevet testet med Glassfish 4.0.

### Endpoint

Når servicen er deployet kan den tilgås via følgende:

* SOAP-operationer: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService
* WSDL: http://&lt;server&gt;:&lt;port&gt;/CatalogingUpdateServices/UpdateService?wsdl

### Resourcer

Servicen har grundlæggende brug for 2 resourcer:

* JDBC resource til råpostrepositoriet. Denne resource skal hedde **jdbc/updateservice/raw-repo**
* JDBC resource til holding+ databasen. Denne resource skal hedde **jdbc/updateservice/holdingitems**

### Logging

Til logning af diverse beskeder anvendes logback. Servicen 2 system properties til det:

* **LOGDIR**: Angiver den fulde path til den folder hvor logfilerne skal placeres.
* **logback.configurationFile**: Den fulde path til logback.xml konfigurationsfilen.

  