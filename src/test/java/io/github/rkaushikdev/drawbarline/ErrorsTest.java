package io.github.rkaushikdev.drawbarline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorsTest {

    static final List<Class<? extends DrawbarlineError>> EVERY = List.of(
            BillingError.class,
            ConfigurationError.class,
            DuplicateCar.class,
            EventError.class,
            MarkError.class,
            RateError.class,
            RegistryError.class,
            SettlementError.class,
            TariffError.class,
            UnknownCar.class);

    static Stream<Class<? extends DrawbarlineError>> every() {
        return EVERY.stream();
    }

    static String codeOf(Class<?> kind) throws ReflectiveOperationException {
        return (String) kind.getDeclaredField("CODE").get(null);
    }

    @ParameterizedTest
    @MethodSource("every")
    void everyErrorDescendsFromTheBase(Class<?> kind) {
        assertThat(DrawbarlineError.class).isAssignableFrom(kind);
    }

    @ParameterizedTest
    @MethodSource("every")
    void everyErrorCarriesItsOwnCode(Class<?> kind) throws ReflectiveOperationException {
        String code = codeOf(kind);
        assertThat(code).isNotEqualTo(DrawbarlineError.CODE);
        assertThat(code).isLowerCase();
    }

    @Test
    void codesAreUniqueAcrossTheTaxonomy() throws ReflectiveOperationException {
        List<String> codes = new java.util.ArrayList<>();
        for (Class<?> kind : EVERY) {
            codes.add(codeOf(kind));
        }
        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    void messageAlonePrintsAsItself() {
        RateError err = new RateError("no rate in effect");
        assertThat(err.getMessage()).isEqualTo("no rate in effect");
    }

    @Test
    void contextIsAppendedInAStableOrder() {
        RateError err = new RateError("no rate in effect", Details.of("on", "2025-01-04", "car_type", "A402"));
        assertThat(err.getMessage()).isEqualTo("no rate in effect (car_type='A402', on='2025-01-04')");
    }

    @Test
    void contextIsReachableAsAMapping() {
        TariffError err = new TariffError("no tariff", Details.of("station", "POWERS"));
        assertThat(err.context()).isEqualTo(Map.of("station", "POWERS"));
        assertThat(err.message()).isEqualTo("no tariff");
    }

    @Test
    void reprNamesTheClassAndTheCode() {
        UnknownCar err = new UnknownCar("never heard of it", Details.of("mark", "WC"));
        assertThat(err.toString()).isEqualTo("UnknownCar('never heard of it', code='unknown_car')");
    }

    @Test
    void unknownAndDuplicateAreRegistryErrors() {
        assertThat(RegistryError.class).isAssignableFrom(UnknownCar.class);
        assertThat(RegistryError.class).isAssignableFrom(DuplicateCar.class);
    }

    @Test
    void catchingTheBaseCatchesALeaf() {
        assertThatThrownBy(() -> {
                    throw new DuplicateCar("already on the register", Details.of("mark", "DBLX", "number", 1042));
                })
                .isInstanceOf(DrawbarlineError.class);
    }

    @Test
    void contextDefaultsToEmpty() {
        assertThat(new EventError("out of order").context()).isEmpty();
    }
}
