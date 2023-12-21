package com.unulearner.backend.security;

import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.Customizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;

@Configuration
@EnableWebSecurity(debug = false)
public class SecurityConfig {
    public static final String MEMBER = "member";

    @Bean	
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {	
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());	
    }	

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
           .authorizeHttpRequests((authz) -> authz
                .requestMatchers(HttpMethod.GET,"/content/english/wordlist/get/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/content/english/dictionary/get/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/content/english/wordlist/add/**").hasRole(MEMBER)
                .requestMatchers(HttpMethod.POST, "/content/english/dictionary/add/**").hasRole(MEMBER)
                .requestMatchers(HttpMethod.DELETE, "/storage/**").hasRole(MEMBER)
                .requestMatchers(HttpMethod.POST, "/storage/**").hasRole(MEMBER)
                .requestMatchers(HttpMethod.GET, "/storage/**").permitAll()
                .anyRequest()
                .authenticated()
            )
            .oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement((sessionManagement) ->
                sessionManagement
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return httpSecurity.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean	
    public AuthenticationManager authenticationManager(HttpSecurity httpSecurity) throws Exception {	
        return httpSecurity.getSharedObject(AuthenticationManagerBuilder.class)	
            .build();	
    }	
}