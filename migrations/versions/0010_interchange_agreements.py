"""interchange agreements

Revision ID: 0010
Revises: 0009
Create Date: 2026-02-02
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0010"
down_revision: str | None = "0009"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "interchange_agreement",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("road", drawbarline.persistence.types.MarkText(length=4), nullable=False),
        sa.Column("junction", sa.String(length=4), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("kinds", sa.String(length=16), nullable=False),
        sa.Column("daily_cap", sa.Integer(), nullable=False),
        sa.Column("opens", sa.Time(), nullable=False),
        sa.Column("closes", sa.Time(), nullable=False),
        sa.Column("hazardous", sa.Boolean(), nullable=False),
        sa.Column("expires", sa.Date(), nullable=True),
        sa.CheckConstraint("daily_cap >= 1", name="ck_agreement_takes_a_car"),
        sa.CheckConstraint(
            "expires is null or expires >= effective", name="ck_agreement_ends_after"
        ),
        sa.CheckConstraint("length(kinds) > 0", name="ck_agreement_takes_a_kind"),
        sa.CheckConstraint("opens < closes", name="ck_agreement_opens_first"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("road", "junction", "effective", name="uq_agreement_one_a_day"),
    )
    with op.batch_alter_table("interchange_agreement", schema=None) as batch_op:
        batch_op.create_index("ix_agreement_junction", ["junction", "effective"], unique=False)
        batch_op.create_index(batch_op.f("ix_interchange_agreement_road"), ["road"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("interchange_agreement", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_interchange_agreement_road"))
        batch_op.drop_index("ix_agreement_junction")

    op.drop_table("interchange_agreement")
