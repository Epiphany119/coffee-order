package com.coffee.common.core.exception;

/**
 * 业务异常
 */
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private Integer code = 500;
    private Object data;

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(Integer code, String message) {
        this(code, message, null);
    }

    public ServiceException(Integer code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public Integer getCode() { return code; }
    public Object getData() { return data; }
}
