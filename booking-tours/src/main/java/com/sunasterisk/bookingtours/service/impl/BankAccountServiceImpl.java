package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.BankAccountRequest;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.entity.UserBankAccount;
import com.sunasterisk.bookingtours.exception.ResourceNotFoundException;
import com.sunasterisk.bookingtours.repository.UserBankAccountRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

    private final UserBankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserBankAccount> getAccountsByUser(String userEmail) {
        User user = findUser(userEmail);
        return bankAccountRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId());
    }

    @Override
    @Transactional
    public UserBankAccount addAccount(String userEmail, BankAccountRequest request) {
        User user = findUser(userEmail);

        List<UserBankAccount> existing =
                bankAccountRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId());

        // Nếu là tài khoản đầu tiên hoặc request muốn set default → clear default cũ
        boolean shouldBeDefault = request.isDefault() || existing.isEmpty();
        if (shouldBeDefault && !existing.isEmpty()) {
            bankAccountRepository.clearDefaultByUserId(user.getId());
        }

        UserBankAccount account = UserBankAccount.builder()
                .user(user)
                .bankName(request.getBankName().trim())
                .accountNumber(request.getAccountNumber().trim())
                .accountHolder(request.getAccountHolder().trim().toUpperCase())
                .isDefault(shouldBeDefault)
                .build();

        return bankAccountRepository.save(account);
    }

    @Override
    @Transactional
    public UserBankAccount updateAccount(String userEmail, Long accountId, BankAccountRequest request) {
        User user = findUser(userEmail);
        UserBankAccount account = findAccountOwnedBy(accountId, user.getId());

        // Nếu muốn set default → clear các tài khoản khác
        if (request.isDefault() && !Boolean.TRUE.equals(account.getIsDefault())) {
            bankAccountRepository.clearDefaultByUserId(user.getId());
            account.setIsDefault(true);
        }

        account.setBankName(request.getBankName().trim());
        account.setAccountNumber(request.getAccountNumber().trim());
        account.setAccountHolder(request.getAccountHolder().trim().toUpperCase());

        return bankAccountRepository.save(account);
    }

    @Override
    @Transactional
    public void deleteAccount(String userEmail, Long accountId) {
        User user = findUser(userEmail);
        UserBankAccount account = findAccountOwnedBy(accountId, user.getId());

        boolean wasDefault = Boolean.TRUE.equals(account.getIsDefault());
        bankAccountRepository.delete(account);

        // Nếu xóa tài khoản default → tự động set default cho tài khoản được tạo sớm nhất còn lại
        if (wasDefault) {
            bankAccountRepository
                    .findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId())
                    .stream()
                    .reduce((first, second) -> second)   // tài khoản cũ nhất (created_at ASC = cuối list DESC)
                    .ifPresent(oldest -> {
                        oldest.setIsDefault(true);
                        bankAccountRepository.save(oldest);
                    });
        }
    }

    @Override
    @Transactional
    public void setDefaultAccount(String userEmail, Long accountId) {
        User user = findUser(userEmail);
        UserBankAccount account = findAccountOwnedBy(accountId, user.getId());

        if (Boolean.TRUE.equals(account.getIsDefault())) {
            return; // Đã là default rồi, không cần làm gì
        }

        bankAccountRepository.clearDefaultByUserId(user.getId());
        account.setIsDefault(true);
        bankAccountRepository.save(account);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    private UserBankAccount findAccountOwnedBy(Long accountId, Long userId) {
        UserBankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account", accountId));

        if (!account.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to access this bank account");
        }
        return account;
    }
}
