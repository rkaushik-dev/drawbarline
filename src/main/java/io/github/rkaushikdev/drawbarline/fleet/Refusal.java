package io.github.rkaushikdev.drawbarline.fleet;

/** A reason the inspector at a junction turns a car back. */
public enum Refusal {
    NOT_REGISTERED("no equipment record"),
    RETIRED("retired from service"),
    NOT_YET_BUILT("dated before the car existed"),
    AGE("past fifty years in interchange service"),
    OVERLOADED("lading is over the load limit"),
    DEFECT("carries a defect that has to be repaired first");

    private final String text;

    Refusal(String text) {
        this.text = text;
    }

    /** The reason as an inspector writes it. */
    public String text() {
        return text;
    }
}
