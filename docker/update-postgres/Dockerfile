FROM docker-i.dbc.dk/dbc-postgres:latest
MAINTAINER Team Iscrum, iscrum@dbc.dk

# postgres on docker registry defines that user and pass must be defined.
ENV POSTGRES_DB=updatestore
ENV POSTGRES_USER=updatestore
ENV POSTGRES_PASSWORD=thePassword

EXPOSE 14332:5432

ADD docker-entrypoint.d/ /docker-entrypoint.d/

