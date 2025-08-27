import logging

import pytest

from drawbarline.config import (
    DEFAULT_DATABASE,
    PREFIX,
    Settings,
    from_environment,
    names,
)
from drawbarline.errors import ConfigurationError
from drawbarline.logs import ROOT, configure, level_of, logger
from drawbarline.marks import ReportingMark


def test_the_defaults_stand_on_their_own():
    found = Settings()
    assert found.database == DEFAULT_DATABASE
    assert found.home == ReportingMark("DBL")
    assert found.log_level == "info"


def test_settings_print_what_matters():
    assert str(Settings()) == "DBL on drawbarline.db at info"


def test_a_database_of_nothing_is_refused():
    with pytest.raises(ConfigurationError):
        Settings(database="   ")


def test_a_log_level_nobody_publishes_is_refused():
    with pytest.raises(ConfigurationError) as caught:
        Settings(log_level="verbose")
    assert "debug" in str(caught.value)


def test_a_home_road_that_cannot_be_a_mark_is_refused():
    with pytest.raises(ConfigurationError):
        Settings(home_road="the drawbar line")


def test_a_plain_name_becomes_a_sqlite_url():
    assert Settings().url == "sqlite+pysqlite:///drawbarline.db"


def test_a_url_is_left_alone():
    found = Settings(database="postgresql+psycopg://host/books")
    assert found.url == "postgresql+psycopg://host/books"


def test_a_database_can_be_swapped_without_touching_the_rest():
    found = Settings(log_level="debug").with_database("other.db")
    assert found.database == "other.db"
    assert found.log_level == "debug"


def test_an_empty_environment_gives_the_defaults():
    assert from_environment({}) == Settings()


def test_the_environment_is_read():
    found = from_environment(
        {
            f"{PREFIX}DATABASE": "books.db",
            f"{PREFIX}HOME_ROAD": "els",
            f"{PREFIX}LOG_LEVEL": "DEBUG",
            f"{PREFIX}ECHO_SQL": "yes",
        }
    )
    assert found.database == "books.db"
    assert found.home == ReportingMark("ELS")
    assert found.log_level == "debug"
    assert found.echo_sql is True


@pytest.mark.parametrize("text", ["1", "yes", "true", "ON"])
def test_a_flag_reads_the_forms_people_write(text):
    assert from_environment({f"{PREFIX}ECHO_SQL": text}).echo_sql is True


@pytest.mark.parametrize("text", ["0", "no", "false", "OFF", ""])
def test_a_flag_reads_the_negative_forms(text):
    assert from_environment({f"{PREFIX}ECHO_SQL": text}).echo_sql is False


def test_a_flag_that_is_neither_is_refused():
    with pytest.raises(ConfigurationError):
        from_environment({f"{PREFIX}ECHO_SQL": "sometimes"})


def test_a_bad_level_in_the_environment_is_refused():
    with pytest.raises(ConfigurationError):
        from_environment({f"{PREFIX}LOG_LEVEL": "chatty"})


def test_every_setting_is_named_for_the_help_text():
    assert names() == [
        f"{PREFIX}DATABASE",
        f"{PREFIX}HOME_ROAD",
        f"{PREFIX}LOG_LEVEL",
        f"{PREFIX}ECHO_SQL",
    ]


def test_every_named_setting_actually_changes_something():
    """A name in the help text that nothing reads is worse than no help text."""
    changed = {
        f"{PREFIX}DATABASE": "somewhere.db",
        f"{PREFIX}HOME_ROAD": "ELS",
        f"{PREFIX}LOG_LEVEL": "debug",
        f"{PREFIX}ECHO_SQL": "yes",
    }
    assert set(changed) == set(names())
    for name, value in changed.items():
        assert from_environment({name: value}) != Settings(), name


def test_a_level_reads_back_as_a_number():
    assert level_of("debug") == logging.DEBUG
    assert level_of("error") == logging.ERROR


def test_a_level_nobody_publishes_has_no_number():
    with pytest.raises(ConfigurationError):
        level_of("chatty")


def test_configuring_sets_the_package_logger():
    found = configure(Settings(log_level="warning"))
    assert found.name == ROOT
    assert found.level == logging.WARNING
    assert len(found.handlers) == 1


def test_configuring_twice_does_not_stack_handlers():
    configure(Settings())
    found = configure(Settings())
    assert len(found.handlers) == 1


def test_the_package_logger_does_not_shout_at_the_root():
    assert configure(Settings()).propagate is False


def test_a_module_logger_sits_under_the_package():
    assert logger("drawbarline.settlement.close").name == "drawbarline.settlement.close"


def test_the_root_name_gives_the_root_logger():
    assert logger(ROOT).name == ROOT


def test_a_logger_outside_the_package_is_refused():
    with pytest.raises(ConfigurationError):
        logger("somebody.else")
