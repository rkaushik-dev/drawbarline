"""the repair price master

Revision ID: 0005
Revises: 0004
Create Date: 2025-08-12
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0005"
down_revision: str | None = "0004"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "depreciation",
        sa.Column("family", sa.String(length=2), nullable=False),
        sa.Column("percent_a_year", sa.Integer(), nullable=False),
        sa.Column("floor_percent", sa.Integer(), nullable=False),
        sa.CheckConstraint("floor_percent between 0 and 100", name="ck_depreciation_floor"),
        sa.CheckConstraint(
            "percent_a_year between 0 and 100", name="ck_depreciation_is_a_percentage"
        ),
        sa.PrimaryKeyConstraint("family"),
    )
    op.create_table(
        "labor_rate",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("road", drawbarline.persistence.types.MarkText(length=4), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("cents_an_hour", sa.Integer(), nullable=False),
        sa.CheckConstraint("cents_an_hour >= 0", name="ck_labor_not_negative"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("road", "effective", name="uq_labor_one_a_day"),
    )
    with op.batch_alter_table("labor_rate", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_labor_rate_road"), ["road"], unique=False)

    op.create_table(
        "price_line",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("job", sa.String(length=4), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("labor_hundredths", sa.Integer(), nullable=False),
        sa.Column("material_cents", sa.Integer(), nullable=False),
        sa.CheckConstraint("labor_hundredths >= 0", name="ck_price_labor_not_negative"),
        sa.CheckConstraint("material_cents >= 0", name="ck_price_material_not_negative"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("job", "effective", name="uq_price_one_a_day"),
    )
    with op.batch_alter_table("price_line", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_price_line_job"), ["job"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("price_line", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_price_line_job"))

    op.drop_table("price_line")
    with op.batch_alter_table("labor_rate", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_labor_rate_road"))

    op.drop_table("labor_rate")
    op.drop_table("depreciation")
