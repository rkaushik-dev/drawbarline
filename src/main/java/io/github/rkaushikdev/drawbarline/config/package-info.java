/**
 * Where the settings come from.
 *
 * <p>Three places, in this order: what the caller passed, then the environment, then the defaults.
 * No file is read because it happens to exist. Every setting is validated where it is read rather
 * than where it is used, so a mistake is reported when the process starts rather than the first
 * time something needs the setting.
 */
package io.github.rkaushikdev.drawbarline.config;
