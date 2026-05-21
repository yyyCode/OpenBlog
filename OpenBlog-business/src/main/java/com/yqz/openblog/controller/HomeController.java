package com.yqz.openblog.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class HomeController {

    @GetMapping(value = "/home", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public java.util.Map<String, String> home() {
        // MVP：返回固定文案给前端检查服务是否可用
        return java.util.Map.of("message", "欢迎来到openblog");
    }
}
