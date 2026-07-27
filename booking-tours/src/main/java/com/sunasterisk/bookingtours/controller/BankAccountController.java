package com.sunasterisk.bookingtours.controller;

import com.sunasterisk.bookingtours.dto.BankAccountRequest;
import com.sunasterisk.bookingtours.entity.UserBankAccount;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/profile/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;

    /**
     * Hiển thị danh sách tài khoản ngân hàng + form thêm mới.
     */
    @GetMapping
    public String listAccounts(Authentication authentication, Model model) {
        List<UserBankAccount> accounts =
                bankAccountService.getAccountsByUser(authentication.getName());

        model.addAttribute("accounts", accounts);
        model.addAttribute("bankAccountForm", new BankAccountRequest());
        return "profile/bank-accounts";
    }

    /**
     * Xử lý thêm tài khoản ngân hàng mới.
     */
    @PostMapping
    public String addAccount(
            Authentication authentication,
            @Valid @ModelAttribute("bankAccountForm") BankAccountRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            List<UserBankAccount> accounts =
                    bankAccountService.getAccountsByUser(authentication.getName());
            model.addAttribute("accounts", accounts);
            model.addAttribute("showAddForm", true);
            return "profile/bank-accounts";
        }

        bankAccountService.addAccount(authentication.getName(), form);
        redirectAttributes.addFlashAttribute("successMessage", "Bank account added successfully!");

        return "redirect:/profile/bank-accounts";
    }

    /**
     * Hiển thị form chỉnh sửa tài khoản ngân hàng.
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(
            Authentication authentication,
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            List<UserBankAccount> accounts =
                    bankAccountService.getAccountsByUser(authentication.getName());

            UserBankAccount target = accounts.stream()
                    .filter(a -> a.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));

            BankAccountRequest form = new BankAccountRequest();
            form.setBankName(target.getBankName());
            form.setAccountNumber(target.getAccountNumber());
            form.setAccountHolder(target.getAccountHolder());
            form.setDefault(Boolean.TRUE.equals(target.getIsDefault()));

            model.addAttribute("accounts", accounts);
            model.addAttribute("bankAccountForm", new BankAccountRequest());
            model.addAttribute("editForm", form);
            model.addAttribute("editId", id);
            return "profile/bank-accounts";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/profile/bank-accounts";
        }
    }

    /**
     * Xử lý cập nhật tài khoản ngân hàng.
     */
    @PostMapping("/{id}/edit")
    public String updateAccount(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @ModelAttribute("editForm") BankAccountRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            List<UserBankAccount> accounts =
                    bankAccountService.getAccountsByUser(authentication.getName());
            model.addAttribute("accounts", accounts);
            model.addAttribute("bankAccountForm", new BankAccountRequest());
            model.addAttribute("editId", id);
            return "profile/bank-accounts";
        }

        try {
            bankAccountService.updateAccount(authentication.getName(), id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Bank account updated successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access denied.");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bank account not found.");
        }

        return "redirect:/profile/bank-accounts";
    }

    /**
     * Xóa tài khoản ngân hàng.
     */
    @PostMapping("/{id}/delete")
    public String deleteAccount(
            Authentication authentication,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            bankAccountService.deleteAccount(authentication.getName(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Bank account deleted successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access denied.");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bank account not found.");
        }

        return "redirect:/profile/bank-accounts";
    }

    /**
     * Đặt tài khoản làm mặc định.
     */
    @PostMapping("/{id}/set-default")
    public String setDefault(
            Authentication authentication,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        try {
            bankAccountService.setDefaultAccount(authentication.getName(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Default bank account updated!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access denied.");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bank account not found.");
        }

        return "redirect:/profile/bank-accounts";
    }
}
