"""the demurrage tariff

Revision ID: 0003
Revises: 0002
Create Date: 2025-05-06
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "tariff",
        sa.Column("code", sa.String(length=8), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("free_days_loaded", sa.Integer(), nullable=False),
        sa.Column("free_days_empty", sa.Integer(), nullable=False),
        sa.CheckConstraint("free_days_empty >= 0", name="ck_tariff_empty_free_time"),
        sa.CheckConstraint("free_days_loaded >= 0", name="ck_tariff_loaded_free_time"),
        sa.PrimaryKeyConstraint("code"),
        sa.UniqueConstraint("effective", name="uq_tariff_one_a_day"),
    )
    op.create_table(
        "average_agreement",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("shipper_code", sa.String(length=8), nullable=False),
        sa.Column("daily_cents", sa.Integer(), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("expires", sa.Date(), nullable=True),
        sa.CheckConstraint("daily_cents >= 0", name="ck_agreement_not_negative"),
        sa.CheckConstraint(
            "expires is null or expires >= effective", name="ck_agreement_ends_after"
        ),
        sa.ForeignKeyConstraint(["shipper_code"], ["shipper.code"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("shipper_code", "effective", name="uq_agreement_one_a_day"),
    )
    with op.batch_alter_table("average_agreement", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_average_agreement_shipper_code"), ["shipper_code"], unique=False
        )

    op.create_table(
        "tariff_step",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("tariff_code", sa.String(length=8), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("through", sa.Integer(), nullable=False),
        sa.Column("cents", sa.Integer(), nullable=False),
        sa.CheckConstraint("cents >= 0", name="ck_step_not_negative"),
        sa.CheckConstraint("through >= 0", name="ck_step_through_not_negative"),
        sa.ForeignKeyConstraint(["tariff_code"], ["tariff.code"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("tariff_code", "position", name="uq_step_one_a_position"),
    )
    with op.batch_alter_table("tariff_step", schema=None) as batch_op:
        batch_op.create_index(
            batch_op.f("ix_tariff_step_tariff_code"), ["tariff_code"], unique=False
        )


def downgrade() -> None:
    with op.batch_alter_table("tariff_step", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_tariff_step_tariff_code"))

    op.drop_table("tariff_step")
    with op.batch_alter_table("average_agreement", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_average_agreement_shipper_code"))

    op.drop_table("average_agreement")
    op.drop_table("tariff")
