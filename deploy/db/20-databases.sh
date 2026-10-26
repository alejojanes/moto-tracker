#!/bin/sh
# Crea una base para Traccar y otra para el backend, cada una con su usuario.
set -e
psql -v ON_ERROR_STOP=1 --username postgres <<SQL
CREATE USER traccar WITH PASSWORD '${TRACCAR_DB_PASSWORD}';
CREATE DATABASE traccar OWNER traccar;
CREATE USER tracker WITH PASSWORD '${TRACKER_DB_PASSWORD}';
CREATE DATABASE tracker OWNER tracker;
SQL
psql -v ON_ERROR_STOP=1 --username postgres --dbname tracker -c "CREATE EXTENSION IF NOT EXISTS postgis;"
