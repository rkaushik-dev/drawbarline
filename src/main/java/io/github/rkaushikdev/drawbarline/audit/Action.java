package io.github.rkaushikdev.drawbarline.audit;

/** The decisions worth remembering a person made. */
public enum Action {
    CLOSED_PERIOD("closed a period"),
    REOPENED_PERIOD("reopened a period"),
    RETIRED_CAR("retired a car"),
    CORRECTED_CAR("corrected an equipment record"),
    LEASED_CAR("filed a lease"),
    ISSUED_EMBARGO("issued an embargo"),
    LIFTED_EMBARGO("lifted an embargo"),
    FILED_CARD("filed a repair card"),
    ANSWERED_EXCEPTION("answered an exception"),
    LOADED_SHEET("loaded an interchange sheet"),
    PUBLISHED_TARIFF("published a tariff"),
    SIGNED_AGREEMENT("signed an interchange agreement");

    private final String text;

    Action(String text) {
        this.text = text;
    }

    /** The decision in words: {@code closed a period}. */
    public String text() {
        return text;
    }
}
