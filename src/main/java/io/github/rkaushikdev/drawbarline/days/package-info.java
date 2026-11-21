/**
 * The calendar the tariff counts on.
 *
 * <p>Demurrage runs on chargeable days, and a Saturday, a Sunday and six holidays a year are not
 * chargeable. The holidays are computed rather than tabled, so no table has to be extended every
 * December. The observance rule is the ordinary federal one: a holiday landing on a Saturday is
 * kept the Friday before, and one landing on a Sunday the Monday after.
 */
package io.github.rkaushikdev.drawbarline.days;
