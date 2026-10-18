package com.doneit.config;

import com.doneit.user.domain.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,DeviceRememberMeServices remember,
            com.doneit.user.application.RememberedDevicesService devices,org.springframework.security.core.session.SessionRegistry sessions) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .rememberMe(config->config.rememberMeServices(remember).key(remember.getKey()))
                .sessionManagement(config->config.maximumSessions(-1).sessionRegistry(sessions).expiredUrl("/login?expired"))
                .addFilterAfter(new RememberedDeviceSessionFilter(remember,devices,sessions),
                        org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                );

        return http.build();
    }

    @Bean public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }
    @Bean public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }
    @Bean public org.springframework.security.web.authentication.rememberme.PersistentTokenRepository persistentTokens(javax.sql.DataSource dataSource,
            com.doneit.user.application.RememberedDevicesService devices) {
        var tokens=new org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl() {
            // Spring bulk-revokes tokens on reuse; the live sessions must also be expired.
            @Override public void removeUserTokens(String username) {devices.revokeOthers(username,null,null);}
        };
        tokens.setDataSource(dataSource);return tokens;
    }
    @Bean public DeviceRememberMeServices rememberMeServices(UserDetailsService users,
            org.springframework.security.web.authentication.rememberme.PersistentTokenRepository tokens,
            com.doneit.user.application.RememberedDevicesService devices) {
        // Persistent cookies use DB validation; this key only pairs Spring's authentication objects and provider.
        return new DeviceRememberMeServices(java.util.UUID.randomUUID().toString(),users,tokens,devices);
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByLogin(username)
                .map(user -> toUserDetails(user.login(), user.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static UserDetails toUserDetails(String login, String passwordHash) {
        return User.withUsername(login)
                .password(passwordHash)
                .authorities("DONEIT_MVP")
                .build();
    }
}
