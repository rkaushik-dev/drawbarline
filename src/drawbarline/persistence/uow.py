"""The unit of work.

One session, one transaction, every repository hanging off it. A caller opens
one, does whatever it came to do, and commits. If it does not commit, nothing
happened, and that is the whole reason the class exists rather than
repositories being handed sessions by whoever is passing.

Committing is explicit. A block that falls out the bottom without committing
rolls back, which is the safe reading of somebody forgetting. It is a little
more typing at every call site and it has caught more than one half written
close.
"""

from __future__ import annotations

from types import TracebackType

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from drawbarline.errors import ConfigurationError
from drawbarline.persistence.base import make_sessions
from drawbarline.persistence.numbering import Numbering
from drawbarline.persistence.repositories.customers import CustomerRepository
from drawbarline.persistence.repositories.events import EventRepository
from drawbarline.persistence.repositories.fleet import FleetRepository
from drawbarline.persistence.repositories.rates import RateRepository
from drawbarline.persistence.repositories.repairs import RepairPricingRepository
from drawbarline.persistence.repositories.settlement import (
    LedgerRepository,
    PeriodRepository,
)
from drawbarline.persistence.repositories.tariffs import TariffRepository
from drawbarline.persistence.repositories.waybills import WaybillRepository


class UnitOfWork:
    """A session and the repositories that ride on it."""

    def __init__(self, engine: Engine) -> None:
        self._sessions = make_sessions(engine)
        self._session: Session | None = None
        self._committed = False

    @property
    def session(self) -> Session:
        if self._session is None:
            raise ConfigurationError("the unit of work is not open")
        return self._session

    @property
    def fleet(self) -> FleetRepository:
        return FleetRepository(self.session)

    @property
    def events(self) -> EventRepository:
        return EventRepository(self.session)

    @property
    def rates(self) -> RateRepository:
        return RateRepository(self.session)

    @property
    def ledger(self) -> LedgerRepository:
        return LedgerRepository(self.session)

    @property
    def periods(self) -> PeriodRepository:
        return PeriodRepository(self.session)

    @property
    def numbering(self) -> Numbering:
        return Numbering(self.session)

    @property
    def customers(self) -> CustomerRepository:
        return CustomerRepository(self.session)

    @property
    def tariffs(self) -> TariffRepository:
        return TariffRepository(self.session)

    @property
    def waybills(self) -> WaybillRepository:
        return WaybillRepository(self.session)

    @property
    def repair_pricing(self) -> RepairPricingRepository:
        return RepairPricingRepository(self.session)

    @property
    def committed(self) -> bool:
        return self._committed

    def __enter__(self) -> UnitOfWork:
        if self._session is not None:
            raise ConfigurationError("the unit of work is already open")
        self._session = self._sessions()
        self._committed = False
        return self

    def commit(self) -> None:
        self.session.commit()
        self._committed = True

    def rollback(self) -> None:
        self.session.rollback()

    def __exit__(
        self,
        kind: type[BaseException] | None,
        value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        session = self.session
        try:
            if kind is not None or not self._committed:
                session.rollback()
        finally:
            session.close()
            self._session = None
