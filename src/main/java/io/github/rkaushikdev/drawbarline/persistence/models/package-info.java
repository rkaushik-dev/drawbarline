/**
 * Every mapped table.
 *
 * <p>One entity a table, each named for the table the migrations create. The entities are found by
 * scanning this package, so a table is mapped exactly when its entity is here, and the migrations
 * test compares the schema these describe with the one the migrations build, table by table and
 * column by column.
 */
package io.github.rkaushikdev.drawbarline.persistence.models;
