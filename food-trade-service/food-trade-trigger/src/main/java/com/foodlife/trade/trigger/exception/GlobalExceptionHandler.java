package com.foodlife.trade.trigger.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.foodlife.trade.types.response.ErrorCode;
import com.foodlife.trade.types.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BlockException.class)
    public Response<Void> handleSentinelBlock(BlockException e) {
        return Response.fail(ErrorCode.TOO_MANY_REQUESTS, "request too frequent, please try again later");
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public Response<Void> handleBadRequest(Exception e) {
        ErrorCode errorCode = classifyClientError(e);
        return Response.fail(errorCode, readMessage(e, errorCode.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Response<Void> handleServiceUnavailable(IllegalStateException e) {
        return Response.fail(ErrorCode.SERVICE_UNAVAILABLE, readMessage(e, ErrorCode.SERVICE_UNAVAILABLE.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleInternalError(Exception e) {
        log.error("unhandled trade-service exception", e);
        return Response.fail(ErrorCode.INTERNAL_ERROR);
    }

    private String readMessage(Exception e, String defaultMessage) {
        if (e instanceof MethodArgumentNotValidException validException
                && validException.getBindingResult().hasFieldErrors()) {
            return validException.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }
        if (e instanceof BindException bindException && bindException.getBindingResult().hasFieldErrors()) {
            return bindException.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }
        return e.getMessage() == null || e.getMessage().trim().isEmpty() ? defaultMessage : e.getMessage();
    }

    private ErrorCode classifyClientError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return ErrorCode.BAD_REQUEST;
        }
        String normalizedMessage = message.toLowerCase();
        if (normalizedMessage.contains("not login") || normalizedMessage.contains("not logged in")) {
            return ErrorCode.UNAUTHORIZED;
        }
        if (normalizedMessage.contains("not found")) {
            return ErrorCode.NOT_FOUND;
        }
        return ErrorCode.BAD_REQUEST;
    }
}
