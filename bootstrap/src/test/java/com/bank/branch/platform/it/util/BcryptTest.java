package com.bank.branch.platform.it.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class BcryptTest {
    public static void main(String[] args) {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("password");
        System.out.println("Hash: " + hash);
        System.out.println("Matches: " + encoder.matches("password", hash));
        System.out.println("Matches old: " + encoder.matches("password", "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"));
    }
}
