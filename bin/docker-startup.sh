#!/bin/bash

MATCHER_ADMIN_PORT="localhost:9980"
MATCHER_THRIFT_PORT="localhost:9999"
NAMERESOLVER_ADMIN_PORT="localhost:9981"
NAMERESOLVER_THRIFT_PORT="localhost:9990"
NAMEFILTER_ADMIN_PORT="localhost:9982"
NAMEFILTER_THRIFT_PORT="localhost:9991"
API_ADMIN_PORT="localhost:9983"

if [[ ${RACK_ENV} = "development" ]]; then
  while [[ "$(pg_isready -h ${DB_HOST} -U ${DB_USER})" =~ "no response" ]]; do
    echo "Waiting for postgres to start..."
    sleep 0.1
  done

  declare -a databases=("development" "test_api" "test_resolver")
  db_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )/../db-migration" && pwd )"

  cd ${db_dir}

  for db in "${databases[@]}"
  do
    psql -h ${DB_HOST} -U ${DB_USER} -tc \
      "SELECT 1 FROM pg_database WHERE datname = '$db'" \
      | grep -q 1 || psql -h $DB_HOST -U $DB_USER -c "CREATE DATABASE $db"

    rake db:migrate RACK_ENV=$db --trace
  done

  rake db:seed RACK_ENV=development --trace

  cd ..
fi

echo "Starting GNIndex API server"

if [[ ${RUN_MODE} = "tests" ]]; then
  sbt "~;test:compile;test:scalastyle;test;
        ;matcher/reStart -admin.port=$MATCHER_ADMIN_PORT -thrift.port=$MATCHER_THRIFT_PORT -names-path=./db-migration/matcher-data/canonical-names.csv -names-datasources-path=./db-migration/matcher-data/canonical-names-with-data-sources.csv \
        ;nameResolver/reStart -thrift.port=$NAMERESOLVER_THRIFT_PORT -admin.port=$NAMERESOLVER_ADMIN_PORT -matcherServiceAddress=$MATCHER_THRIFT_PORT \
        ;nameFilter/reStart -thrift.port=$NAMEFILTER_THRIFT_PORT -admin.port=$NAMEFILTER_ADMIN_PORT -matcherServiceAddress=$MATCHER_THRIFT_PORT \
        ;api/reStart -nameresolverServiceAddress=$NAMERESOLVER_THRIFT_PORT -namefilterServiceAddress=$NAMEFILTER_THRIFT_PORT -admin.port=$API_ADMIN_PORT"
else
  sbt "~;matcher/reStart -admin.port=$MATCHER_ADMIN_PORT -thrift.port=$MATCHER_THRIFT_PORT -names-path=./db-migration/matcher-data/canonical-names.csv -names-datasources-path=./db-migration/matcher-data/canonical-names-with-data-sources.csv \
        ;nameResolver/reStart -thrift.port=$NAMERESOLVER_THRIFT_PORT -admin.port=$NAMERESOLVER_ADMIN_PORT -matcherServiceAddress=$MATCHER_THRIFT_PORT \
        ;nameFilter/reStart -thrift.port=$NAMEFILTER_THRIFT_PORT -admin.port=$NAMEFILTER_ADMIN_PORT -matcherServiceAddress=$MATCHER_THRIFT_PORT \
        ;api/reStart -nameresolverServiceAddress=$NAMERESOLVER_THRIFT_PORT -namefilterServiceAddress=$NAMEFILTER_THRIFT_PORT -admin.port=$API_ADMIN_PORT"
fi
