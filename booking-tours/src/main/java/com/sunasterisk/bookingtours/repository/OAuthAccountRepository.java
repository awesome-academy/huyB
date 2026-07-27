package com.sunasterisk.bookingtours.repository;

import com.sunasterisk.bookingtours.entity.OAuthAccount;
import com.sunasterisk.bookingtours.entity.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    /**
     * Tìm OAuthAccount theo provider + provider_user_id.
     * Dùng để kiểm tra user đã link OAuth account của provider này chưa.
     */
    Optional<OAuthAccount> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
