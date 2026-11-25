package in.dscribe.admissions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  @Bean
  org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
      strictInputTypes() {
    return builder ->
        builder.postConfigurer(
            mapper -> {
              mapper
                  .coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Integer)
                  .setCoercion(
                      com.fasterxml.jackson.databind.cfg.CoercionInputShape.String,
                      com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
              // Text fields must not silently turn JSON booleans or numbers into names.
              var textCoercion =
                  mapper.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual);
              for (var shape :
                  new com.fasterxml.jackson.databind.cfg.CoercionInputShape[] {
                    com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean,
                    com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
                    com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float
                  }) {
                textCoercion.setCoercion(
                    shape, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
              }
            });
  }

  @Bean
  PasswordEncoder encoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  SecurityContextRepository contextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, SecurityContextRepository repository, JdbcTemplate db) throws Exception {
    http.securityContext(c -> c.securityContextRepository(repository))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/**", "/api/health", "/error")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> json(res, 401, "Sign in to access patient records."))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            json(
                                res,
                                403,
                                "Your security token expired. Refresh the page and try again.")))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src"
                                + " 'self' data:; font-src 'self'; connect-src 'self';"
                                + " frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
        .logout(l -> l.disable());
    // Administrator password rotation revokes every existing login.
    http.addFilterBefore(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null
                && auth.getPrincipal() instanceof AuthController.StaffIdentity identity) {
              var versions =
                  db.queryForList(
                      "SELECT credential_version FROM staff WHERE email=?",
                      Integer.class,
                      identity.email());
              if (versions.isEmpty() || versions.get(0) != identity.version()) {
                req.getSession().invalidate();
                SecurityContextHolder.clearContext();
                json(res, 401, "Your password changed. Please sign in again.");
                return;
              }
            }
            if (req.getRequestURI().startsWith("/api/")) res.setHeader("Cache-Control", "no-store");
            chain.doFilter(req, res);
          }
        },
        AuthorizationFilter.class);
    return http.build();
  }

  static void json(HttpServletResponse res, int status, String message) throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.getWriter().write("{\"message\":\"" + message + "\"}");
  }
}
