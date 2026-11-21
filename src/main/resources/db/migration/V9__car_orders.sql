-- Car orders: a shipper asking for cars of a kind on a day, and the day the order was placed.

CREATE TABLE car_order (
    number INTEGER NOT NULL,
    shipper_code CHARACTER VARYING(8) NOT NULL,
    industry_code CHARACTER VARYING(12) NOT NULL,
    kind CHARACTER VARYING(1) NOT NULL,
    cars INTEGER NOT NULL,
    wanted_on DATE NOT NULL,
    placed_on DATE NOT NULL,
    cancelled_on DATE,
    CONSTRAINT pk_car_order PRIMARY KEY (number),
    CONSTRAINT ck_order_numbered_from_one CHECK (number >= 1),
    CONSTRAINT ck_order_asks_for_a_car CHECK (cars >= 1)
);
CREATE INDEX ix_car_order_industry_code ON car_order (industry_code);
CREATE INDEX ix_car_order_shipper_code ON car_order (shipper_code);
CREATE INDEX ix_order_wanted ON car_order (wanted_on);

ALTER TABLE car_order ADD CONSTRAINT fk_car_order_industry_code_industry
    FOREIGN KEY (industry_code) REFERENCES industry (code) ON DELETE RESTRICT;
ALTER TABLE car_order ADD CONSTRAINT fk_car_order_shipper_code_shipper
    FOREIGN KEY (shipper_code) REFERENCES shipper (code) ON DELETE RESTRICT;
