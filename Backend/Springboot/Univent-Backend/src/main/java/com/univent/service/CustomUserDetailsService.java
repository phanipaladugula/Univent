package com.univent.service;

import com.univent.model.entity.User;
import com.univent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String emailHash) throws UsernameNotFoundException {
        System.out.println("Looking for user with email hash: " + emailHash);

        User user = userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email hash: " + emailHash));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmailHash())
                .password("") // No password, using OTP
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}