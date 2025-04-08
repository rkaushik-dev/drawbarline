"""Turning rows into domain objects and back.

Both directions live together on purpose. A mapper written one way is a
mapper that is wrong the other way within a month, and the test that matters
is the round trip: an object stored and read back has to be equal to what went
in. Keeping the two functions next to each other makes that test obvious to
write and obvious to keep passing.
"""

from drawbarline.persistence.mappers.fleet import (
    car_from_row,
    lease_rows_for,
    ownership_from_rows,
    row_from_car,
)

__all__ = ["car_from_row", "lease_rows_for", "ownership_from_rows", "row_from_car"]
