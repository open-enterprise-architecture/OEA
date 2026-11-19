package io.oea.core.metamodel;

/**
 * The origin of existing metamodel content that has the identifier or the name of content a
 * package brings: its scope ({@code built-in}, {@code imported} or {@code organization}) and the
 * package it came from.
 */
public record ContentOwner(String scope, String packageId) {
}
