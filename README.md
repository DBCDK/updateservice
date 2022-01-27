<img src="http://www.dbc.dk/logo.png" alt="DBC" title="DBC" align="right">

# UpdateService

Updateservice is a REST webservice which is used for validating and persisting marc records send to rawrepo.

### Developers how-to

You need to clone the following projects to the same root folder:
* updateservice (https://github.com/DBCDK/updateservice)
* opencat-business (https://github.com/DBCDK/opencat-business)
* ocb-tools (https://github.com/DBCDK/ocb-tools)

Start by doing a maven install on ocb-tools. In most cases you won't have to mess around with ocb-tools after this as you just need the compiled artifact.

Next you have to run transpile-templates in opencat-business. If you are running on a machine with the dbc-jsshell package install run bin/run-js-tests.sh. Otherwise use bin/run-js-tests-in-docker.sh (assuming you have docker installed).

Now you are ready to start working on updateservice.

Bootstrapping the environment (first ever run only):
* `docker/bin/create_update_network.sh`
* `docker/bin/build-update-docker-parent.sh`

Then start work by doing:
* `docker/bin/start_dev_docker.sh`

And then, after each change in the code:
* `docker/bin/start-local-docker.sh`

When you are done, remove your test containers by running:
* `docker/bin/stop_dev_docker.sh`

### Endpoints

When the service is deployed the following endpoints are available:

```
/UpdateService/rest/api/v1/updateservice
/UpdateService/rest/api/v1/getschemas
/UpdateService/rest/api/v1/openbuildservice
```

### Environment variables

The following environment variables must be defined:

- **VIPCORE_CACHE_AGE** Amount of hours to cache results from vipcore (default 8 hours)
- **VIPCORE_ENDPOINT** Url to vipcore rest service
- **OPENNUMBERROLL_URL** Url to opennumberroll service
- **IDP_SERVICE_URL** Url to IDP rest service
- **HOLDINGS_ITEMS_DB_URL** Url to the holdings items database
- **RAWREPO_DB_URL URL** Url to the rawrepo database
- **SOLR_URL** Url to the update/FBS solr
- **SOLR_BASIS_URL** Url to the basis solr
- **UPDATE_DB_URL** Url to the update database
- **OPENCAT_BUSINESS_URL** Url to the opencat-business rest service
- **JAVA_MAX_HEAP_SIZE** Amount of memory which the underlying payara allocates, e.g. `8G`

Database urls must be of the format `username:password@database-host:post/database-name`
