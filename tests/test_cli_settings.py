from click.testing import CliRunner

from drawbarline.api.app import app_from
from drawbarline.cli import cli
from drawbarline.config import PREFIX, Settings


def run(*args, env=None):
    return CliRunner(env=env).invoke(cli, list(args))


def test_the_settings_command_shows_the_defaults(tmp_path):
    found = run("--database", str(tmp_path / "books.db"), "settings")
    assert found.exit_code == 0
    assert "home road  DBL" in found.output
    assert "log level  info" in found.output


def test_the_settings_command_lists_where_each_one_comes_from(tmp_path):
    found = run("--database", str(tmp_path / "books.db"), "settings")
    for name in (f"{PREFIX}DATABASE", f"{PREFIX}HOME_ROAD", f"{PREFIX}LOG_LEVEL"):
        assert name in found.output


def test_the_url_is_shown_with_its_driver(tmp_path):
    found = run("--database", "books.db", "settings")
    assert "url        sqlite+pysqlite:///books.db" in found.output


def test_the_environment_supplies_the_database(tmp_path):
    database = str(tmp_path / "fromenv.db")
    found = run("settings", env={f"{PREFIX}DATABASE": database})
    assert database in found.output


def test_the_option_beats_the_environment(tmp_path):
    chosen = str(tmp_path / "chosen.db")
    found = run("--database", chosen, "settings", env={f"{PREFIX}DATABASE": "ignored.db"})
    assert chosen in found.output
    assert "ignored.db" not in found.output


def test_the_environment_supplies_the_home_road(tmp_path):
    found = run("settings", env={f"{PREFIX}HOME_ROAD": "els"})
    assert "home road  els" in found.output


def test_a_home_road_that_cannot_be_a_mark_is_refused():
    found = run("settings", env={f"{PREFIX}HOME_ROAD": "the drawbar line"})
    assert found.exit_code != 0


def test_the_log_level_can_be_set_on_the_command_line(tmp_path):
    found = run("--database", str(tmp_path / "b.db"), "--log-level", "debug", "settings")
    assert "log level  debug" in found.output


def test_a_log_level_nobody_publishes_is_refused(tmp_path):
    found = run("--database", str(tmp_path / "b.db"), "--log-level", "chatty", "settings")
    assert found.exit_code != 0


def test_the_environment_supplies_the_log_level(tmp_path):
    found = run("settings", env={f"{PREFIX}LOG_LEVEL": "warning"})
    assert "log level  warning" in found.output


def test_a_command_still_works_with_no_options_at_all(tmp_path):
    database = str(tmp_path / "books.db")
    assert run("--database", database, "init").exit_code == 0
    assert "cars      0" in run("--database", database, "status").output


def test_the_application_stands_up_from_settings(tmp_path):
    from fastapi.testclient import TestClient

    from drawbarline.persistence.base import create_all, make_engine

    database = str(tmp_path / "api.db")
    create_all(make_engine(f"sqlite+pysqlite:///{database}"))
    app = app_from(Settings(database=database))
    with TestClient(app) as client:
        assert client.get("/health").json()["status"] == "up"
        assert client.get("/books").json()["cars"] == 0


def test_the_application_reads_the_environment_when_told_nothing(tmp_path, monkeypatch):
    from drawbarline.persistence.base import create_all, make_engine

    database = str(tmp_path / "env.db")
    create_all(make_engine(f"sqlite+pysqlite:///{database}"))
    monkeypatch.setenv(f"{PREFIX}DATABASE", database)
    assert app_from() is not None


def test_echoing_sql_is_off_unless_asked(tmp_path):
    found = run("--database", str(tmp_path / "b.db"), "settings")
    assert "echo sql   False" in found.output


def test_echoing_sql_can_be_turned_on(tmp_path):
    found = run("settings", env={f"{PREFIX}ECHO_SQL": "yes"})
    assert "echo sql   True" in found.output


def test_a_flag_that_is_neither_yes_nor_no_is_refused():
    assert run("settings", env={f"{PREFIX}ECHO_SQL": "sometimes"}).exit_code != 0
