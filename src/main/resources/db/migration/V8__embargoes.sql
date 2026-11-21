-- Embargoes: one row a restriction, lifted by setting the day it expires.

CREATE TABLE embargo (
    number INTEGER NOT NULL,
    effective DATE NOT NULL,
    reason CHARACTER VARYING(200) NOT NULL,
    station CHARACTER VARYING(4),
    commodity_family CHARACTER VARYING(7),
    road CHARACTER VARYING(4),
    expires DATE,
    permits_allowed BOOLEAN NOT NULL,
    CONSTRAINT pk_embargo PRIMARY KEY (number),
    CONSTRAINT ck_embargo_numbered_from_one CHECK (number >= 1),
    CONSTRAINT ck_embargo_restricts_something
        CHECK (station is not null or commodity_family is not null or road is not null),
    CONSTRAINT ck_embargo_ends_after CHECK (expires is null or expires >= effective)
);
CREATE INDEX ix_embargo_effective ON embargo (effective);
