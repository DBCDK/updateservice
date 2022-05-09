FROM docker-dbc.artifacts.dbccloud.dk/dbc-postgres-13:latest

# postgres on docker registry defines that user and pass must be defined.
ENV POSTGRES_DB=updatestore
ENV POSTGRES_USER=updatestore
ENV POSTGRES_PASSWORD=thePassword

ADD docker-entrypoint.d/ /docker-entrypoint.d/

LABEL MAINTAINER="meta-scrum@dbc.dk"
