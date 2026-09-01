package com.coffee.common.core.exception;

import com.coffee.common.core.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public Result<Void> handleServiceException(ServiceException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleIllegalStateException(IllegalStateException e) {
        return Result.error(400, e.getMessage());
    }

    /** 将 Spring 在进入控制器前拦截的坏请求也统一成客户端可处理的 400。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadableMessage(HttpMessageNotReadableException e) {
        return Result.error(400, "请求体格式无效");
    }

    @ExceptionHandler({MissingRequestValueException.class, MethodArgumentTypeMismatchException.class})
    public Result<Void> handleInvalidRequestParameter(Exception e) {
        return Result.error(400, "请求参数无效或缺失");
    }

    /** 文件在进入控制器前超过 Spring multipart 限制时，返回可直接展示给用户的提示。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return Result.error(400, "上传文件不能超过 5MB，请压缩图片后重试");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unhandled server exception", e);
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
