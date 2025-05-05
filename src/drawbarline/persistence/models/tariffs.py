"""The demurrage tariff tables.

A tariff is a header and its steps, and the steps are ordered by the day they
run through, with the last one running to the end. That last step is stored
with a zero, which is the same convention the domain uses, so a row and a step
object say the same thing without a translation nobody would remember.

Average agreements sit beside the tariff rather than inside it. A tariff is
published to everybody. An agreement is signed with one shipper, and the two
change on different days for different reasons.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import CheckConstraint, Date, ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from drawbarline.persistence.base import Base


class TariffRow(Base):
    """One published schedule."""

    __tablename__ = "tariff"
    __table_args__ = (
        UniqueConstraint("effective", name="uq_tariff_one_a_day"),
        CheckConstraint("free_days_loaded >= 0", name="ck_tariff_loaded_free_time"),
        CheckConstraint("free_days_empty >= 0", name="ck_tariff_empty_free_time"),
    )

    code: Mapped[str] = mapped_column(String(8), primary_key=True)
    effective: Mapped[date] = mapped_column(Date)
    free_days_loaded: Mapped[int] = mapped_column()
    free_days_empty: Mapped[int] = mapped_column()

    steps: Mapped[list[TariffStepRow]] = relationship(
        back_populates="tariff",
        cascade="all, delete-orphan",
        order_by="TariffStepRow.position",
    )

    def __repr__(self) -> str:
        return f"<TariffRow {self.code} from {self.effective}>"


class TariffStepRow(Base):
    """One price band inside a tariff."""

    __tablename__ = "tariff_step"
    __table_args__ = (
        UniqueConstraint("tariff_code", "position", name="uq_step_one_a_position"),
        CheckConstraint("through >= 0", name="ck_step_through_not_negative"),
        CheckConstraint("cents >= 0", name="ck_step_not_negative"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    tariff_code: Mapped[str] = mapped_column(
        String(8), ForeignKey("tariff.code", ondelete="CASCADE"), index=True
    )
    position: Mapped[int] = mapped_column()
    through: Mapped[int] = mapped_column()
    cents: Mapped[int] = mapped_column()

    tariff: Mapped[TariffRow] = relationship(back_populates="steps")

    def __repr__(self) -> str:
        return f"<TariffStepRow {self.tariff_code} through {self.through}>"


class AverageAgreementRow(Base):
    """A shipper billed on a monthly balance instead of car by car."""

    __tablename__ = "average_agreement"
    __table_args__ = (
        UniqueConstraint("shipper_code", "effective", name="uq_agreement_one_a_day"),
        CheckConstraint("daily_cents >= 0", name="ck_agreement_not_negative"),
        CheckConstraint("expires is null or expires >= effective", name="ck_agreement_ends_after"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    shipper_code: Mapped[str] = mapped_column(
        String(8), ForeignKey("shipper.code", ondelete="CASCADE"), index=True
    )
    daily_cents: Mapped[int] = mapped_column()
    effective: Mapped[date] = mapped_column(Date)
    expires: Mapped[date | None] = mapped_column(Date, default=None)

    def __repr__(self) -> str:
        return f"<AverageAgreementRow {self.shipper_code} from {self.effective}>"
