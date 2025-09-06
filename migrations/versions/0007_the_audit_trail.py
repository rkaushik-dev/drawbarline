"""the audit trail

Revision ID: 0007
Revises: 0006
Create Date: 2025-09-01
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0007"
down_revision: str | None = "0006"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "audit",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("at", drawbarline.persistence.types.Utc(timezone=True), nullable=False),
        sa.Column("actor", sa.String(length=32), nullable=False),
        sa.Column("action", sa.String(length=24), nullable=False),
        sa.Column("subject", sa.String(length=60), nullable=False),
        sa.Column("detail", sa.String(length=200), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    with op.batch_alter_table("audit", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_audit_action"), ["action"], unique=False)
        batch_op.create_index("ix_audit_actor_at", ["actor", "at"], unique=False)
        batch_op.create_index(batch_op.f("ix_audit_at"), ["at"], unique=False)
        batch_op.create_index("ix_audit_subject", ["subject"], unique=False)


def downgrade() -> None:
    with op.batch_alter_table("audit", schema=None) as batch_op:
        batch_op.drop_index("ix_audit_subject")
        batch_op.drop_index(batch_op.f("ix_audit_at"))
        batch_op.drop_index("ix_audit_actor_at")
        batch_op.drop_index(batch_op.f("ix_audit_action"))

    op.drop_table("audit")
