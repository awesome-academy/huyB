package com.sunasterisk.bookingtours.controller.admin;

import com.sunasterisk.bookingtours.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller quản lý các trang của khu vực Admin.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DashboardService dashboardService;

    /**
     * Hiển thị trang Admin Dashboard với các số liệu thống kê tổng quan:
     * tổng user, tổng tour, booking hôm nay và doanh thu tháng hiện tại.
     *
     * @param model Spring MVC model để truyền dữ liệu sang view
     * @return tên Thymeleaf template {@code admin/dashboard}
     */
    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.getStats());
        return "admin/dashboard";
    }
}
