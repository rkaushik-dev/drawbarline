/**
 * The calendar the tariff counts on.
 *
 * <p>Demurrage runs on chargeable days, and a Saturday, a Sunday, the six standard holidays, any
 * holiday a tariff adds to them and any day the road declares closed are not chargeable. The
 * holidays are computed rather than tabled, so no table has to be extended every December, and the
 * observance rule is the ordinary federal one: a holiday landing on a Saturday is kept the Friday
 * before, and one landing on a Sunday the Monday after. A declared day, a yard shut by weather, is
 * dated and carries its reason.
 */
package io.github.rkaushikdev.drawbarline.days;
