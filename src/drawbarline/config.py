"""Where the settings come from.

Three places, in this order: what the caller passed, then the environment,
then the defaults. There is no fourth place and there is no file that is read
if it happens to exist, because a setting that comes from a file nobody
mentioned is the one that takes an afternoon to find.

Everything is validated at the point it is read rather than at the point it is
used. A log level of ``verbose`` is a mistake somebody made when they started
the process, and telling them then is worth more than telling them the first
time something wants to log a warning.
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass, replace

from drawbarline.errors import ConfigurationError, MarkError
from drawbarline.marks import ReportingMark

PREFIX = "DRAWBARLINE_"

DEFAULT_DATABASE = "drawbarline.db"
DEFAULT_HOME = "DBL"
DEFAULT_LEVEL = "info"

LEVELS = ("debug", "info", "warning", "error")


@dataclass(frozen=True, slots=True)
class Settings:
    """Everything the process needs to know before it does anything."""

    database: str = DEFAULT_DATABASE
    home_road: str = DEFAULT_HOME
    log_level: str = DEFAULT_LEVEL
    echo_sql: bool = False

    def __post_init__(self) -> None:
        if not self.database.strip():
            raise ConfigurationError("a database is required")
        if self.log_level not in LEVELS:
            raise ConfigurationError("no such log level", level=self.log_level, known=list(LEVELS))
        try:
            ReportingMark.parse(self.home_road)
        except MarkError as bad:
            # A mark that will not read is a configuration problem here, not a
            # data problem, and it should reach the operator wearing that name.
            raise ConfigurationError(
                "the home road is not a reporting mark", home_road=self.home_road
            ) from bad

    @property
    def home(self) -> ReportingMark:
        return ReportingMark.parse(self.home_road)

    @property
    def url(self) -> str:
        """The sqlalchemy url, which is the database name with a driver on it."""
        if "://" in self.database:
            return self.database
        return f"sqlite+pysqlite:///{self.database}"

    def with_database(self, database: str) -> Settings:
        return replace(self, database=database)

    def __str__(self) -> str:
        return f"{self.home_road} on {self.database} at {self.log_level}"


def _flag(text: str, name: str) -> bool:
    cleaned = text.strip().lower()
    if cleaned in ("1", "yes", "true", "on"):
        return True
    if cleaned in ("0", "no", "false", "off", ""):
        return False
    raise ConfigurationError("that is not a yes or a no", setting=name, given=text)


def from_environment(env: Mapping[str, str] | None = None) -> Settings:
    """Read the settings out of the environment, falling back to the defaults."""
    found = os.environ if env is None else env
    return Settings(
        database=found.get(f"{PREFIX}DATABASE", DEFAULT_DATABASE),
        home_road=found.get(f"{PREFIX}HOME_ROAD", DEFAULT_HOME),
        log_level=found.get(f"{PREFIX}LOG_LEVEL", DEFAULT_LEVEL).strip().lower(),
        echo_sql=_flag(found.get(f"{PREFIX}ECHO_SQL", ""), f"{PREFIX}ECHO_SQL"),
    )


def names() -> list[str]:
    """Every environment variable this application reads, for the help text."""
    return [
        f"{PREFIX}DATABASE",
        f"{PREFIX}HOME_ROAD",
        f"{PREFIX}LOG_LEVEL",
        f"{PREFIX}ECHO_SQL",
    ]
