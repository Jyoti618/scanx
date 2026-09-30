package com.scanx;

/** An error the user should see: wrong input, not allowed, not found ... */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public final int status;
    public final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, "FORBIDDEN", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(409, code, message);
    }

}
