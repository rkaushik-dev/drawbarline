package io.github.rkaushikdev.drawbarline.fleet;

/**
 * The edit between the two identifiers on every equipment record.
 *
 * <p>The mechanical designation and the car type code are entered by different people from
 * different paperwork, so they disagree more often than anyone would like. {@link #agrees} catches
 * it before a rate is looked up on the wrong one.
 */
public final class Aar {

    private Aar() {}

    /** Whether the two identifiers on a record are talking about the same kind of car. */
    public static boolean agrees(MechanicalDesignation designation, CarTypeCode typeCode) {
        return designation.kind() == typeCode.kind();
    }
}
