package com.shiyoushu.snack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "请求失败" : e.getReason())); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception e) {
        String message = e instanceof IllegalArgumentException ? e.getMessage() : "请求参数格式不正确，请核对原文及录入来源";
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<?> notFound(Exception e) { return ResponseEntity.status(404).body(Map.of("message", "接口不存在")); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> oversized(Exception e) { return ResponseEntity.status(413).body(Map.of("message", "图片不能超过4MB，请压缩或重新拍摄")); }
    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<?> upload(Exception e) { return ResponseEntity.badRequest().body(Map.of("message", "图片上传不完整，请重新拍摄或手动输入")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        LOG.error("API request failed ({})", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of("message", "服务暂不可用，请稍后重试"));
    }
}
