package com.cornerstone.core.config;

import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.config.properties.JiaopeiProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JiaopeiProperties properties;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final StubAuthenticationFilter stubAuthenticationFilter;

    public SecurityConfig(JiaopeiProperties properties, RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          StubAuthenticationFilter stubAuthenticationFilter) {
        this.properties = properties;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.stubAuthenticationFilter = stubAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> publicPaths = properties.getSecurity().getPublicPaths();
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(formLogin -> formLogin.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(stubAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
