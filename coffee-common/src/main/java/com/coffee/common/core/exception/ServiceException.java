package com.coffee.common.core.exception;

/**
 * 业务异常
 */
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private Integer code = 500;

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() { return code; }
}
