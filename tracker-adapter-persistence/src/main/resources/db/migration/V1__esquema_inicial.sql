-- Esquema propio del backend. Traccar usa su propia base de datos (traccar), esta es "tracker".
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE trackers (
    imei        VARCHAR(20) PRIMARY KEY,
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE positions (
    id          BIGSERIAL PRIMARY KEY,
    imei        VARCHAR(20)      NOT NULL REFERENCES trackers (imei),
    fix_time    TIMESTAMPTZ      NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    speed_kmh   DOUBLE PRECISION NOT NULL,
    course      DOUBLE PRECISION NOT NULL,
    source      VARCHAR(10)      NOT NULL CHECK (source IN ('GPS', 'NETWORK', 'INVALID')),
    ignition    BOOLEAN,
    satellites  INTEGER,
    received_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    geom        geography(Point, 4326) GENERATED ALWAYS AS
                (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    CONSTRAINT uq_positions_fix UNIQUE (imei, fix_time)
);

CREATE INDEX ix_positions_reliable ON positions (imei, fix_time DESC) WHERE source = 'GPS';
CREATE INDEX ix_positions_geom ON positions USING GIST (geom);

CREATE TABLE geofences (
    id             UUID PRIMARY KEY,
    imei           VARCHAR(20)      NOT NULL REFERENCES trackers (imei),
    name           TEXT             NOT NULL,
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    radius_meters  DOUBLE PRECISION NOT NULL,
    active         BOOLEAN          NOT NULL
);

CREATE TABLE alerts (
    id         BIGSERIAL PRIMARY KEY,
    imei       VARCHAR(20) NOT NULL REFERENCES trackers (imei),
    type       VARCHAR(30) NOT NULL,
    time       TIMESTAMPTZ NOT NULL,
    message    TEXT        NOT NULL,
    latitude   DOUBLE PRECISION,
    longitude  DOUBLE PRECISION
);

CREATE INDEX ix_alerts_recent ON alerts (imei, time DESC);
