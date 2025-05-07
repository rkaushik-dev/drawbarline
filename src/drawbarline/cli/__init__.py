"""The command line the general office actually uses.

Importing this package registers every command group on the root. A group in a
module nobody imports is a command that silently does not exist, which is the
same trap the model package has and it is solved the same way.
"""

from drawbarline.cli import (
    events,  # noqa: F401  registers the events group
    fleet,  # noqa: F401  registers the fleet group
)
from drawbarline.cli.main import cli, run

__all__ = ["cli", "run"]
