package com.sunasterisk.bookingtours.exception;

import lombok.Getter;

/**
 * Thrown when a registration attempt uses an e-mail address that is already
 * taken – either detected by the pre-read optimistic check <em>or</em> by
 * catching a {@code DataIntegrityViolationException} from the database unique
 * constraint (covers the concurrent-registration race condition).
 */
@Getter
public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("Email is already registered: " + email);
        this.email = email;
    }
}
