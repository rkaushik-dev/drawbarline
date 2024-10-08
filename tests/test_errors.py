import pytest

from drawbarline.errors import (
    BillingError,
    ConfigurationError,
    DrawbarlineError,
    DuplicateCar,
    EventError,
    MarkError,
    RateError,
    RegistryError,
    SettlementError,
    TariffError,
    UnknownCar,
)

EVERY = [
    BillingError,
    ConfigurationError,
    DuplicateCar,
    EventError,
    MarkError,
    RateError,
    RegistryError,
    SettlementError,
    TariffError,
    UnknownCar,
]


@pytest.mark.parametrize("cls", EVERY)
def test_every_error_descends_from_the_base(cls):
    assert issubclass(cls, DrawbarlineError)


@pytest.mark.parametrize("cls", EVERY)
def test_every_error_carries_its_own_code(cls):
    assert cls.code != DrawbarlineError.code
    assert cls.code.islower()


def test_codes_are_unique_across_the_taxonomy():
    codes = [cls.code for cls in EVERY]
    assert len(codes) == len(set(codes))


def test_message_alone_prints_as_itself():
    err = RateError("no rate in effect")
    assert str(err) == "no rate in effect"


def test_context_is_appended_in_a_stable_order():
    err = RateError("no rate in effect", car_type="A402", on="2025-01-04")
    assert str(err) == "no rate in effect (car_type='A402', on='2025-01-04')"


def test_context_is_reachable_as_a_mapping():
    err = TariffError("no tariff", station="POWERS")
    assert err.context == {"station": "POWERS"}
    assert err.message == "no tariff"


def test_repr_names_the_class_and_the_code():
    err = UnknownCar("never heard of it", mark="WC")
    assert repr(err) == "UnknownCar('never heard of it', code='unknown_car')"


def test_unknown_and_duplicate_are_registry_errors():
    assert issubclass(UnknownCar, RegistryError)
    assert issubclass(DuplicateCar, RegistryError)


def test_catching_the_base_catches_a_leaf():
    with pytest.raises(DrawbarlineError):
        raise DuplicateCar("already on the register", mark="DBLX", number=1042)


def test_context_defaults_to_empty():
    assert EventError("out of order").context == {}
