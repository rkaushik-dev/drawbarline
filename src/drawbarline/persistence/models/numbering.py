"""The counters that hand out document numbers.

An invoice number and a statement number are not decoration. A customer keeps
their invoices in a folder in number order and a gap in that folder is a
question we have to answer, so the counters are gapless: a number is handed
out inside the transaction that uses it and given back if that transaction
rolls away.

One row a counter, and the row is updated in place, which is the only table in
the schema that works that way. It is the exception the rest of the design
avoids and it earns it, because the alternative is a select of the maximum
number issued and that races two clerks into the same invoice number.
"""

from __future__ import annotations

from sqlalchemy import CheckConstraint, String
from sqlalchemy.orm import Mapped, mapped_column

from drawbarline.persistence.base import Base


class CounterRow(Base):
    """Where one series of document numbers has got to."""

    __tablename__ = "counter"
    __table_args__ = (CheckConstraint("next_number >= 1", name="ck_counter_numbered_from_one"),)

    name: Mapped[str] = mapped_column(String(24), primary_key=True)
    next_number: Mapped[int] = mapped_column()

    def __repr__(self) -> str:
        return f"<CounterRow {self.name} next {self.next_number}>"
