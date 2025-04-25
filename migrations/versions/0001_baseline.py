"""the schema as it stands at the first release

Revision ID: 0001
Revises:
Create Date: 2025-04-25
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "car",
        sa.Column("mark", drawbarline.persistence.types.CarMarkText(length=11), nullable=False),
        sa.Column("owner", drawbarline.persistence.types.MarkText(length=4), nullable=False),
        sa.Column(
            "designation", drawbarline.persistence.types.DesignationText(length=4), nullable=False
        ),
        sa.Column(
            "type_code", drawbarline.persistence.types.TypeCodeText(length=4), nullable=False
        ),
        sa.Column("built", sa.Date(), nullable=False),
        sa.Column("rebuilt", sa.Date(), nullable=True),
        sa.Column("light_weight", drawbarline.persistence.types.WeightPounds(), nullable=False),
        sa.Column("gross_rail_load", drawbarline.persistence.types.WeightPounds(), nullable=False),
        sa.Column("outside_length", drawbarline.persistence.types.LengthInches(), nullable=False),
        sa.CheckConstraint("light_weight < gross_rail_load", name="ck_car_light_under_gross"),
        sa.CheckConstraint("outside_length > 0", name="ck_car_has_length"),
        sa.PrimaryKeyConstraint("mark"),
    )
    with op.batch_alter_table("car", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_car_owner"), ["owner"], unique=False)
        batch_op.create_index(batch_op.f("ix_car_type_code"), ["type_code"], unique=False)

    op.create_table(
        "car_hire_band",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column(
            "type_code", drawbarline.persistence.types.TypeCodeText(length=4), nullable=False
        ),
        sa.Column("from_age", sa.Integer(), nullable=False),
        sa.Column("to_age", sa.Integer(), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("hourly_tenths", sa.Integer(), nullable=False),
        sa.Column("mileage_mills", sa.Integer(), nullable=False),
        sa.CheckConstraint("from_age >= 0", name="ck_band_age_from_zero"),
        sa.CheckConstraint("hourly_tenths >= 0", name="ck_band_hourly_not_negative"),
        sa.CheckConstraint("mileage_mills >= 0", name="ck_band_mileage_not_negative"),
        sa.CheckConstraint("to_age >= from_age", name="ck_band_age_climbs"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("type_code", "from_age", "effective", name="uq_band_not_filed_twice"),
    )
    with op.batch_alter_table("car_hire_band", schema=None) as batch_op:
        batch_op.create_index("ix_band_lookup", ["type_code", "effective"], unique=False)

    op.create_table(
        "closed_period",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("period_year", sa.Integer(), nullable=False),
        sa.Column("period_month", sa.Integer(), nullable=False),
        sa.Column("closed_on", sa.Date(), nullable=False),
        sa.CheckConstraint("period_month between 1 and 12", name="ck_closed_month"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("period_year", "period_month", name="uq_closed_once"),
    )
    op.create_table(
        "counter",
        sa.Column("name", sa.String(length=24), nullable=False),
        sa.Column("next_number", sa.Integer(), nullable=False),
        sa.CheckConstraint("next_number >= 1", name="ck_counter_numbered_from_one"),
        sa.PrimaryKeyConstraint("name"),
    )
    op.create_table(
        "posting",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("road", drawbarline.persistence.types.MarkText(length=4), nullable=False),
        sa.Column("period_year", sa.Integer(), nullable=False),
        sa.Column("period_month", sa.Integer(), nullable=False),
        sa.Column("kind", sa.String(length=12), nullable=False),
        sa.Column("amount", drawbarline.persistence.types.MoneyCents(), nullable=False),
        sa.Column("posted_on", sa.Date(), nullable=False),
        sa.Column("reference", sa.String(length=60), nullable=False),
        sa.CheckConstraint("period_month between 1 and 12", name="ck_posting_month"),
        sa.PrimaryKeyConstraint("id"),
    )
    with op.batch_alter_table("posting", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_posting_road"), ["road"], unique=False)
        batch_op.create_index(
            "ix_posting_road_period", ["road", "period_year", "period_month"], unique=False
        )

    op.create_table(
        "reopening",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("period_year", sa.Integer(), nullable=False),
        sa.Column("period_month", sa.Integer(), nullable=False),
        sa.Column("reopened_on", sa.Date(), nullable=False),
        sa.Column("reason", sa.String(length=200), nullable=False),
        sa.CheckConstraint("period_month between 1 and 12", name="ck_reopening_month"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_table(
        "car_event",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("car_mark", drawbarline.persistence.types.CarMarkText(length=11), nullable=False),
        sa.Column("kind", sa.String(length=16), nullable=False),
        sa.Column("happened", drawbarline.persistence.types.Utc(timezone=True), nullable=False),
        sa.Column("station", sa.String(length=4), nullable=False),
        sa.Column("road", drawbarline.persistence.types.MarkText(length=4), nullable=True),
        sa.Column("industry", sa.String(length=12), nullable=True),
        sa.Column("waybill", sa.Integer(), nullable=True),
        sa.Column("tenths", sa.Integer(), nullable=True),
        sa.Column("note", sa.String(length=120), nullable=False),
        sa.CheckConstraint("tenths is null or tenths > 0", name="ck_event_movement_covers_ground"),
        sa.CheckConstraint("waybill is null or waybill > 0", name="ck_event_waybill_numbered"),
        sa.ForeignKeyConstraint(["car_mark"], ["car.mark"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("car_mark", "kind", "happened", "station", name="uq_event_not_twice"),
    )
    with op.batch_alter_table("car_event", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_car_event_car_mark"), ["car_mark"], unique=False)
        batch_op.create_index("ix_event_car_when", ["car_mark", "happened"], unique=False)
        batch_op.create_index("ix_event_when", ["happened"], unique=False)

    op.create_table(
        "lease",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("car_mark", drawbarline.persistence.types.CarMarkText(length=11), nullable=False),
        sa.Column("lessee", drawbarline.persistence.types.MarkText(length=4), nullable=False),
        sa.Column("began", sa.Date(), nullable=False),
        sa.Column("ended", sa.Date(), nullable=True),
        sa.CheckConstraint("ended is null or ended >= began", name="ck_lease_ends_after"),
        sa.ForeignKeyConstraint(["car_mark"], ["car.mark"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("car_mark", "began", name="uq_lease_one_start_a_day"),
    )
    with op.batch_alter_table("lease", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_lease_car_mark"), ["car_mark"], unique=False)
        batch_op.create_index(batch_op.f("ix_lease_lessee"), ["lessee"], unique=False)

    op.create_table(
        "retirement",
        sa.Column("car_mark", drawbarline.persistence.types.CarMarkText(length=11), nullable=False),
        sa.Column("on", sa.Date(), nullable=False),
        sa.Column("reason", sa.String(length=120), nullable=False),
        sa.ForeignKeyConstraint(["car_mark"], ["car.mark"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("car_mark"),
    )


def downgrade() -> None:
    op.drop_table("retirement")
    with op.batch_alter_table("lease", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_lease_lessee"))
        batch_op.drop_index(batch_op.f("ix_lease_car_mark"))

    op.drop_table("lease")
    with op.batch_alter_table("car_event", schema=None) as batch_op:
        batch_op.drop_index("ix_event_when")
        batch_op.drop_index("ix_event_car_when")
        batch_op.drop_index(batch_op.f("ix_car_event_car_mark"))

    op.drop_table("car_event")
    op.drop_table("reopening")
    with op.batch_alter_table("posting", schema=None) as batch_op:
        batch_op.drop_index("ix_posting_road_period")
        batch_op.drop_index(batch_op.f("ix_posting_road"))

    op.drop_table("posting")
    op.drop_table("counter")
    op.drop_table("closed_period")
    with op.batch_alter_table("car_hire_band", schema=None) as batch_op:
        batch_op.drop_index("ix_band_lookup")

    op.drop_table("car_hire_band")
    with op.batch_alter_table("car", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_car_type_code"))
        batch_op.drop_index(batch_op.f("ix_car_owner"))

    op.drop_table("car")
