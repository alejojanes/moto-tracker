package com.mototracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Un solo usuario (el dueño de la moto). La app web usa sesión con cookie y CSRF;
 * el webhook de Traccar no lleva sesión y se valida con su token en el adaptador.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(TrackerProperties properties, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(User.withUsername(properties.username())
                .password(encoder.encode(properties.password()))
                .roles("OWNER")
                .build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, TrackerProperties properties, UserDetailsService users)
            throws Exception {
        PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(path.matcher("/ingest/**")).permitAll()
                        .requestMatchers(path.matcher("/actuator/health/**")).permitAll()
                        .requestMatchers(path.matcher("/login.html"), path.matcher("/css/**"),
                                path.matcher("/icons/**"), path.matcher("/js/login.js"), path.matcher("/manifest.webmanifest"),
                                path.matcher("/favicon.ico"), path.matcher("/error")).permitAll()
                        .anyRequest().hasRole("OWNER"))
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers(path.matcher("/ingest/**")))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .successHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((req, res, e) -> res.setStatus(HttpStatus.UNAUTHORIZED.value()))
                        .permitAll())
                .rememberMe(remember -> remember
                        .key(properties.rememberMeKey())
                        .userDetailsService(users)
                        .tokenValiditySeconds((int) Duration.ofDays(30).toSeconds()))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login.html"))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), path.matcher("/api/**")))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data: blob: https://*.tile.openstreetmap.org; "
                                + "connect-src 'self' https://*.tile.openstreetmap.org; worker-src 'self' blob:; frame-ancestors 'none'")));
        return http.build();
    }

    /** Fuerza a generar la cookie XSRF-TOKEN para que la app la lea y la envíe en cada cambio. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
