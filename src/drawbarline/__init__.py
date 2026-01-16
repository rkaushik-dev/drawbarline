"""Car hire, demurrage and interchange settlement for a short line railroad.

The package is laid out by what a thing is rather than by what kind of thing
it is. Car hire lives in one place, demurrage in another, and the persistence
and the two interfaces sit at the edges knowing about everything while nothing
in the middle knows about them.

Nothing here imports anything. Importing the package should not open a
database, read an environment variable or configure logging, and it does not.
"""

__version__ = "0.1.0"
