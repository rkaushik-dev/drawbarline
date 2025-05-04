"""the shippers and the track we place their cars on

Revision ID: 0002
Revises: 0001
Create Date: 2025-04-28
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "shipper",
        sa.Column("code", sa.String(length=8), nullable=False),
        sa.Column("name", sa.String(length=60), nullable=False),
        sa.Column("average_agreement", sa.Boolean(), nullable=False),
        sa.PrimaryKeyConstraint("code"),
    )
    op.create_table(
        "industry",
        sa.Column("code", sa.String(length=12), nullable=False),
        sa.Column("name", sa.String(length=60), nullable=False),
        sa.Column("station", sa.String(length=4), nullable=False),
        sa.Column("shipper_code", sa.String(length=8), nullable=False),
        sa.Column("spots", sa.Integer(), nullable=False),
        sa.Column("switched", sa.String(length=32), nullable=False),
        sa.CheckConstraint("length(switched) > 0", name="ck_industry_is_switched"),
        sa.CheckConstraint("spots >= 1", name="ck_industry_holds_a_car"),
        sa.ForeignKeyConstraint(["shipper_code"], ["shipper.code"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("code"),
    )
    with op.batch_alter_table("industry", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_industry_shipper_code"), ["shipper_code"], unique=False
        )
        batch_op.create_index(batch_op.f("ix_industry_station"), ["station"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("industry", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_industry_station"))
        batch_op.drop_index(batch_op.f("ix_industry_shipper_code"))

    op.drop_table("industry")
    op.drop_table("shipper")
