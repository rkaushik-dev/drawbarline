"""waybills and their routes

Revision ID: 0004
Revises: 0003
Create Date: 2025-07-24
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0004"
down_revision: str | None = "0003"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "waybill",
        sa.Column("number", sa.Integer(), nullable=False),
        sa.Column("dated", sa.Date(), nullable=False),
        sa.Column("car_mark", drawbarline.persistence.types.CarMarkText(length=11), nullable=False),
        sa.Column("shipper", sa.String(length=60), nullable=False),
        sa.Column("consignee", sa.String(length=60), nullable=False),
        sa.Column("lading_stcc", sa.String(length=7), nullable=True),
        sa.Column("lading_name", sa.String(length=60), nullable=True),
        sa.Column("lading_pounds", drawbarline.persistence.types.WeightPounds(), nullable=True),
        sa.Column("lading_bulk", sa.Boolean(), nullable=False),
        sa.CheckConstraint(
            "lading_stcc is null or lading_pounds > 0", name="ck_waybill_lading_weighs"
        ),
        sa.CheckConstraint("number >= 1", name="ck_waybill_numbered_from_one"),
        sa.ForeignKeyConstraint(["car_mark"], ["car.mark"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("number"),
    )
    with op.batch_alter_table("waybill", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_waybill_car_mark"), ["car_mark"], unique=False)
        batch_op.create_index(batch_op.f("ix_waybill_dated"), ["dated"], unique=False)

    op.create_table(
        "route_stop",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("waybill_number", sa.Integer(), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("station", sa.String(length=4), nullable=False),
        sa.CheckConstraint("position >= 0", name="ck_stop_position_not_negative"),
        sa.ForeignKeyConstraint(["waybill_number"], ["waybill.number"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("waybill_number", "position", name="uq_stop_one_a_position"),
    )
    with op.batch_alter_table("route_stop", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_route_stop_waybill_number"), ["waybill_number"], unique=False
        )


def downgrade() -> None:
    with op.batch_alter_table("route_stop", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_route_stop_waybill_number"))

    op.drop_table("route_stop")
    with op.batch_alter_table("waybill", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_waybill_dated"))
        batch_op.drop_index(batch_op.f("ix_waybill_car_mark"))

    op.drop_table("waybill")
