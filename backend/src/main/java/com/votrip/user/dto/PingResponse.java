package com.votrip.user.dto;

/**
 * What /api/v1/ping returns: the Firebase UID this service resolved from the caller's token.
 *
 * <p>The UID is echoed back from the verified token, not from anything the client sent about
 * itself - that is the whole point of the endpoint.
 */
public record PingResponse(String uid) {
}
