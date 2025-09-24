"""embargoes

Revision ID: 0008
Revises: 0007
Create Date: 2025-09-25
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0008"
down_revision: str | None = "0007"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "embargo",
        sa.Column("number", sa.Integer(), nullable=False),
        sa.Column("effective", sa.Date(), nullable=False),
        sa.Column("reason", sa.String(length=200), nullable=False),
        sa.Column("station", sa.String(length=4), nullable=True),
        sa.Column("commodity_family", sa.String(length=7), nullable=True),
        sa.Column("road", drawbarline.persistence.types.MarkText(length=4), nullable=True),
        sa.Column("expires", sa.Date(), nullable=True),
        sa.Column("permits_allowed", sa.Boolean(), nullable=False),
        sa.CheckConstraint("expires is null or expires >= effective", name="ck_embargo_ends_after"),
        sa.CheckConstraint("number >= 1", name="ck_embargo_numbered_from_one"),
        sa.CheckConstraint(
            "station is not null or commodity_family is not null or road is not null",
            name="ck_embargo_restricts_something",
        ),
        sa.PrimaryKeyConstraint("number"),
    )
    with op.batch_alter_table("embargo", schema=None) as batch_op:
        batch_op.create_index("ix_embargo_effective", ["effective"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("embargo", schema=None) as batch_op:
        batch_op.drop_index("ix_embargo_effective")

    op.drop_table("embargo")
