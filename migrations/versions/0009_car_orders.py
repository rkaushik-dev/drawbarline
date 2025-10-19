"""car orders

Revision ID: 0009
Revises: 0008
Create Date: 2025-10-20
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0009"
down_revision: str | None = "0008"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "car_order",
        sa.Column("number", sa.Integer(), nullable=False),
        sa.Column("shipper_code", sa.String(length=8), nullable=False),
        sa.Column("industry_code", sa.String(length=12), nullable=False),
        sa.Column("kind", sa.String(length=1), nullable=False),
        sa.Column("cars", sa.Integer(), nullable=False),
        sa.Column("wanted_on", sa.Date(), nullable=False),
        sa.Column("placed_on", sa.Date(), nullable=False),
        sa.Column("cancelled_on", sa.Date(), nullable=True),
        sa.CheckConstraint("cars >= 1", name="ck_order_asks_for_a_car"),
        sa.CheckConstraint("number >= 1", name="ck_order_numbered_from_one"),
        sa.ForeignKeyConstraint(["industry_code"], ["industry.code"], ondelete="RESTRICT"),
        sa.ForeignKeyConstraint(["shipper_code"], ["shipper.code"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("number"),
    )
    with op.batch_alter_table("car_order", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_car_order_industry_code"), ["industry_code"], unique=False
        )
        batch_op.create_index(
            batch_op.f("ix_car_order_shipper_code"), ["shipper_code"], unique=False
        )
        batch_op.create_index("ix_order_wanted", ["wanted_on"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("car_order", schema=None) as batch_op:
        batch_op.drop_index("ix_order_wanted")
        batch_op.drop_index(batch_op.f("ix_car_order_shipper_code"))
        batch_op.drop_index(batch_op.f("ix_car_order_industry_code"))

    op.drop_table("car_order")
