"""repair cards and the exceptions against them

Revision ID: 0006
Revises: 0005
Create Date: 2025-08-24
"""

from __future__ import annotations

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

import drawbarline.persistence.types

revision: str = "0006"
down_revision: str | None = "0005"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "repair_card",
        sa.Column("number", sa.Integer(), nullable=False),
        sa.Column("car_mark", drawbarline.persistence.types.CarMarkText(length=11), nullable=False),
        sa.Column("performed_by", drawbarline.persistence.types.MarkText(length=4), nullable=False),
        sa.Column("performed_on", sa.Date(), nullable=False),
        sa.Column("station", sa.String(length=4), nullable=False),
        sa.CheckConstraint("number >= 1", name="ck_card_numbered_from_one"),
        sa.ForeignKeyConstraint(["car_mark"], ["car.mark"], ondelete="RESTRICT"),
        sa.PrimaryKeyConstraint("number"),
    )
    with op.batch_alter_table("repair_card", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_repair_card_car_mark"), ["car_mark"], unique=False)
        batch_op.create_index(
            batch_op.f("ix_repair_card_performed_by"), ["performed_by"], unique=False
        )
        batch_op.create_index(
            batch_op.f("ix_repair_card_performed_on"), ["performed_on"], unique=False
        )

    op.create_table(
        "defect",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("card_number", sa.Integer(), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("job", sa.String(length=4), nullable=False),
        sa.Column("why_made", sa.String(length=2), nullable=False),
        sa.Column("quantity", sa.Integer(), nullable=False),
        sa.Column("overridden", sa.String(length=16), nullable=True),
        sa.Column("part_years", sa.Integer(), nullable=False),
        sa.Column("note", sa.String(length=120), nullable=False),
        sa.CheckConstraint("part_years >= 0", name="ck_defect_part_age"),
        sa.CheckConstraint("quantity >= 1", name="ck_defect_covers_a_part"),
        sa.ForeignKeyConstraint(["card_number"], ["repair_card.number"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("card_number", "position", name="uq_defect_one_a_position"),
    )
    with op.batch_alter_table("defect", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_defect_card_number"), ["card_number"], unique=False)

    op.create_table(
        "objection",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("card_number", sa.Integer(), nullable=False),
        sa.Column("line", sa.Integer(), nullable=False),
        sa.Column("ground", sa.String(length=20), nullable=False),
        sa.Column("disputed", drawbarline.persistence.types.MoneyCents(), nullable=False),
        sa.Column("filed_on", sa.Date(), nullable=False),
        sa.Column("note", sa.String(length=200), nullable=False),
        sa.CheckConstraint("disputed > 0", name="ck_objection_disputes_something"),
        sa.ForeignKeyConstraint(["card_number"], ["repair_card.number"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("card_number", "line", name="uq_objection_one_a_line"),
    )
    with op.batch_alter_table("objection", schema=None) as batch_op:
        batch_op.create_index(batch_op.f("ix_objection_card_number"), ["card_number"], unique=False)

    op.create_table(
        "resolution",
        sa.Column("objection_id", sa.Integer(), nullable=False),
        sa.Column("outcome", sa.String(length=16), nullable=False),
        sa.Column("allowed", drawbarline.persistence.types.MoneyCents(), nullable=False),
        sa.CheckConstraint("allowed >= 0", name="ck_resolution_not_negative"),
        sa.ForeignKeyConstraint(["objection_id"], ["objection.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("objection_id"),
    )


def downgrade() -> None:
    op.drop_table("resolution")
    with op.batch_alter_table("objection", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_objection_card_number"))

    op.drop_table("objection")
    with op.batch_alter_table("defect", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_defect_card_number"))

    op.drop_table("defect")
    with op.batch_alter_table("repair_card", schema=None) as batch_op:
        batch_op.drop_index(batch_op.f("ix_repair_card_performed_on"))
        batch_op.drop_index(batch_op.f("ix_repair_card_performed_by"))
        batch_op.drop_index(batch_op.f("ix_repair_card_car_mark"))

    op.drop_table("repair_card")
