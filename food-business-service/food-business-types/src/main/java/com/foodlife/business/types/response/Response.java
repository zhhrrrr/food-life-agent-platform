package com.foodlife.business.types.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class Response<T> implements Serializable {

    private String code;
    private String message;
    private T data;

    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setCode("0000");
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> Response<T> fail(String code, String message) {
        Response<T> response = new Response<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
