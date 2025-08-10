import pytest
from click.testing import CliRunner

from drawbarline.cli import cli

CAR = [
    "fleet",
    "add",
    "CN 400100",
    "--designation",
    "LO",
    "--type-code",
    "L351",
    "--built",
    "2020-06-01",
    "--light-weight",
    "63500",
    "--gross-rail-load",
    "286000",
    "--length",
    "59-6",
]


def a_record(kind="RC", number="400100", stamp="20250106", clock="0700"):
    return f"IC{kind}{'CN':<4}{number}{stamp}{clock}041207{0:05d}{'':<12}".ljust(80)


def a_file(*records):
    header = f"HD{'CN':<4}{'ESC':<4}20250106{41:04d}".ljust(80)
    lines = [header, *records, f"TR{len(records):06d}".ljust(80)]
    return "\n".join(lines) + "\n"


def run(database, *args):
    return CliRunner().invoke(cli, ["--database", database, *args])


@pytest.fixture
def books(tmp_path):
    database = str(tmp_path / "books.db")
    CliRunner().invoke(cli, ["--database", database, "init"])
    run(database, *CAR)
    other = list(CAR)
    other[2] = "CN 400101"
    run(database, *other)
    return database


def write(tmp_path, name, text):
    path = tmp_path / name
    path.write_text(text, encoding="utf-8")
    return str(path)


def test_a_clean_sheet_is_taken(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record()))
    found = run(books, "interchange", "receive", path)
    assert found.exit_code == 0
    assert "took 1 records" in found.output


def test_the_records_reach_the_log(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record()))
    run(books, "interchange", "receive", path)
    assert "events    1" in run(books, "status").output


def test_the_sheet_names_the_road_and_junction(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record()))
    found = run(books, "interchange", "receive", path)
    assert "from CN at ESC" in found.output


def test_a_sheet_with_a_bad_line_is_refused(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record(), a_record(kind="ZZ")))
    found = run(books, "interchange", "receive", path)
    assert found.exit_code != 0
    assert "did not read cleanly" in found.output


def test_a_refused_sheet_records_nothing(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record(), a_record(kind="ZZ")))
    run(books, "interchange", "receive", path)
    assert "events    0" in run(books, "status").output


def test_the_good_half_can_be_taken_on_purpose(books, tmp_path):
    path = write(
        tmp_path, "in.txt", a_file(a_record(), a_record(kind="ZZ"), a_record(number="400101"))
    )
    found = run(books, "interchange", "receive", path, "--partial")
    assert found.exit_code == 0
    assert "took 2 records" in found.output


def test_a_sheet_whose_trailer_disagrees_is_refused(books, tmp_path):
    text = a_file(a_record()).replace("TR000001", "TR000009")
    path = write(tmp_path, "in.txt", text)
    found = run(books, "interchange", "receive", path)
    assert found.exit_code != 0
    assert "trailer says 9" in found.output


def test_a_sheet_with_no_header_is_refused(books, tmp_path):
    path = write(tmp_path, "in.txt", a_record() + "\n")
    found = run(books, "interchange", "receive", path)
    assert found.exit_code != 0


def test_a_record_for_a_car_nobody_filed_is_refused(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record(number="999999")))
    found = run(books, "interchange", "receive", path)
    assert found.exit_code != 0
    assert "events    0" in run(books, "status").output


def test_a_file_that_does_not_exist_is_refused(books):
    assert run(books, "interchange", "receive", "nowhere.txt").exit_code != 0


def test_a_day_of_interchange_is_written_back(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record(), a_record(number="400101")))
    run(books, "interchange", "receive", path)
    found = run(
        books,
        "interchange",
        "send",
        "--road",
        "CN",
        "--junction",
        "ESC",
        "--day",
        "2025-01-06",
    )
    assert found.exit_code == 0
    assert found.output.startswith("HDCN")
    assert found.output.count("IC") == 2


def test_the_written_sheet_reads_back(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record()))
    run(books, "interchange", "receive", path)
    out = tmp_path / "out.txt"
    found = run(
        books,
        "interchange",
        "send",
        "--road",
        "CN",
        "--junction",
        "ESC",
        "--day",
        "2025-01-06",
        "--out",
        str(out),
    )
    assert "wrote 1 records" in found.output
    assert out.read_text(encoding="utf-8").startswith("HDCN")


def test_a_day_with_no_interchange_writes_an_empty_sheet(books):
    found = run(
        books,
        "interchange",
        "send",
        "--road",
        "CN",
        "--junction",
        "ESC",
        "--day",
        "2025-02-06",
    )
    assert "TR000000" in found.output


def test_another_road_gets_none_of_our_records(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record()))
    run(books, "interchange", "receive", path)
    found = run(
        books,
        "interchange",
        "send",
        "--road",
        "ELS",
        "--junction",
        "ESC",
        "--day",
        "2025-01-06",
    )
    assert "TR000000" in found.output


def test_a_day_that_cannot_be_read_is_refused(books):
    found = run(
        books,
        "interchange",
        "send",
        "--road",
        "CN",
        "--junction",
        "ESC",
        "--day",
        "yesterday",
    )
    assert found.exit_code != 0
