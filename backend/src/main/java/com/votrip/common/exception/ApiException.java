package com.votrip.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for failures this API reports deliberately, as opposed to unexpected ones.
 *
 * <p>Carrying the status and the machine-readable code on the exception keeps
 * {@link GlobalExceptionHandler} from growing a handler method per domain error - domain packages
 * subclass this instead.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
