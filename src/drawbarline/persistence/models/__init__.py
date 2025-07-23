"""Every mapped table.

Importing this package puts every table on the metadata. That is not a
convenience, it is the whole contract: a table declared in a module nobody
imports is absent from ``create_all`` and absent from what alembic compares
against, and the failure looks like a missing table at runtime months later.

So every model module is imported here, the star imports are deliberate, and
the test that walks the metadata is what stops one being forgotten.
"""

from drawbarline.persistence.models.customers import IndustryRow, ShipperRow
from drawbarline.persistence.models.events import EventRow
from drawbarline.persistence.models.fleet import CarRow, LeaseRow, RetirementRow
from drawbarline.persistence.models.numbering import CounterRow
from drawbarline.persistence.models.rates import RateBandRow
from drawbarline.persistence.models.settlement import (
    ClosedPeriodRow,
    PostingRow,
    ReopeningRow,
)
from drawbarline.persistence.models.tariffs import (
    AverageAgreementRow,
    TariffRow,
    TariffStepRow,
)
from drawbarline.persistence.models.waybill import RouteStopRow, WaybillRow

__all__ = [
    "AverageAgreementRow",
    "CarRow",
    "ClosedPeriodRow",
    "CounterRow",
    "EventRow",
    "IndustryRow",
    "LeaseRow",
    "PostingRow",
    "RateBandRow",
    "ReopeningRow",
    "RetirementRow",
    "RouteStopRow",
    "ShipperRow",
    "TariffRow",
    "TariffStepRow",
    "WaybillRow",
]
