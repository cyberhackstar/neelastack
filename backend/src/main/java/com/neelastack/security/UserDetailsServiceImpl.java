package com.neelastack.security;

import com.neelastack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Defensive normalization at this boundary too: AuthService.login already normalizes
        // before reaching here, but this service is also the generic Spring Security entry
        // point (e.g. any future auth provider), so it must not depend on callers remembering to.
        String normalized = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));
    }
}
