"""Every mapped table.

Importing this package puts every table on the metadata. That is not a
convenience, it is the whole contract: a table declared in a module nobody
imports is absent from ``create_all`` and absent from what alembic compares
against, and the failure looks like a missing table at runtime months later.

So every model module is imported here, the star imports are deliberate, and
the test that walks the metadata is what stops one being forgotten.
"""

from drawbarline.persistence.models.events import EventRow
from drawbarline.persistence.models.fleet import CarRow, LeaseRow, RetirementRow

__all__ = ["CarRow", "EventRow", "LeaseRow", "RetirementRow"]
