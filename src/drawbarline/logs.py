"""Logging, configured once and never at import time.

A library that configures logging when it is imported takes the decision away
from whoever is using it, so nothing in this package touches the root logger
except :func:`configure`, and only the command line and the application
factory call that.

The format carries the logger name because the useful question when something
goes wrong at a close is which part of the close, and the module name answers
it without anybody having to add a prefix to every message.
"""

from __future__ import annotations

import logging

from drawbarline.config import LEVELS, Settings
from drawbarline.errors import ConfigurationError

FORMAT = "%(asctime)s %(levelname)-7s %(name)s %(message)s"
TIME_FORMAT = "%Y-%m-%d %H:%M:%S"
ROOT = "drawbarline"


def level_of(name: str) -> int:
    if name not in LEVELS:
        raise ConfigurationError("no such log level", level=name, known=list(LEVELS))
    return getattr(logging, name.upper())


def configure(settings: Settings) -> logging.Logger:
    """Set the package logger up. Safe to call twice, which a test will."""
    found = logging.getLogger(ROOT)
    found.setLevel(level_of(settings.log_level))
    found.handlers.clear()
    handler = logging.StreamHandler()
    handler.setFormatter(logging.Formatter(FORMAT, TIME_FORMAT))
    found.addHandler(handler)
    found.propagate = False
    return found


def logger(name: str) -> logging.Logger:
    """The logger for one module, under the package root."""
    if name == ROOT:
        return logging.getLogger(ROOT)
    if not name.startswith(f"{ROOT}."):
        raise ConfigurationError("that logger is outside the package", name=name)
    return logging.getLogger(name)
