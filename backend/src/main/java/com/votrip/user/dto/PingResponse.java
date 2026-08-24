package com.votrip.user.dto;

/**
 * What /api/v1/ping returns: the caller identity this service resolved from the verified token.
 *
 * <p>Both fields are echoed back from the verified token, not from anything the client sent about
 * itself - that is the whole point of the endpoint.
 *
 * <p>{@code email} is nullable: a Firebase account created through a phone-number or anonymous
 * provider carries no email claim. Callers must not treat it as a stable identifier - {@code uid}
 * is the identity this service keys on.
 */
public record PingResponse(String uid, String email) {
}
