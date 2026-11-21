-- The shippers we bill demurrage to, and the private track we place their cars on.

CREATE TABLE shipper (
    code CHARACTER VARYING(8) NOT NULL,
    name CHARACTER VARYING(60) NOT NULL,
    average_agreement BOOLEAN NOT NULL,
    CONSTRAINT pk_shipper PRIMARY KEY (code)
);

CREATE TABLE industry (
    code CHARACTER VARYING(12) NOT NULL,
    name CHARACTER VARYING(60) NOT NULL,
    station CHARACTER VARYING(4) NOT NULL,
    shipper_code CHARACTER VARYING(8) NOT NULL,
    spots INTEGER NOT NULL,
    switched CHARACTER VARYING(32) NOT NULL,
    CONSTRAINT pk_industry PRIMARY KEY (code),
    CONSTRAINT ck_industry_holds_a_car CHECK (spots >= 1),
    CONSTRAINT ck_industry_is_switched CHECK (length(switched) > 0)
);
CREATE INDEX ix_industry_shipper_code ON industry (shipper_code);
CREATE INDEX ix_industry_station ON industry (station);

ALTER TABLE industry ADD CONSTRAINT fk_industry_shipper_code_shipper
    FOREIGN KEY (shipper_code) REFERENCES shipper (code) ON DELETE CASCADE;
