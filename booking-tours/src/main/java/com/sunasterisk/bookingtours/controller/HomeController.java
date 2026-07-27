package com.sunasterisk.bookingtours.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Home", description = "Trang chủ")
@Controller
public class HomeController {

    @Operation(summary = "Hiển thị trang chủ")
    @GetMapping("/")
    public String home() {
        return "index";
    }
}
