package in.dscribe.admissions;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController implements ApplicationRunner {
  public record StaffIdentity(String email, int version) implements Serializable {}

  record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final SecurityContextRepository contexts;
  private final String dummyHash;

  @Value("${app.admin-email}")
  String adminEmail;

  @Value("${app.admin-password}")
  String adminPassword;

  @Value("${app.rotate-admin-password:false}")
  boolean rotateAdminPassword;

  @Value("${app.base-url}")
  String baseUrl;

  @Value("${app.production}")
  boolean production;

  private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();

  private record Window(long start, int count) {}

  AuthController(JdbcTemplate db, PasswordEncoder encoder, SecurityContextRepository contexts) {
    this.db = db;
    this.encoder = encoder;
    this.contexts = contexts;
    dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  @Override
  public void run(ApplicationArguments args) {
    if (production && (!baseUrl.startsWith("https://") || baseUrl.contains("onrender.com")))
      throw new IllegalStateException(
          "Set APP_BASE_URL to your HTTPS custom domain before production launch.");
    if (!adminEmail.isBlank() && !adminPassword.isBlank()) {
      if (!adminEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
          || adminPassword.length() < 12
          || adminPassword.length() > 128)
        throw new IllegalStateException(
            "Set a valid ADMIN_EMAIL and a 12-128 character ADMIN_PASSWORD.");
      String email = adminEmail.trim().toLowerCase(Locale.ROOT);
      if (db.queryForObject("SELECT COUNT(*) FROM staff WHERE email=?", Integer.class, email) == 0)
        db.update(
            "INSERT INTO staff(email,password_hash) VALUES (?,?)",
            email,
            encoder.encode(adminPassword));
      else if (rotateAdminPassword)
        db.update(
            "UPDATE staff SET password_hash=?, credential_version=credential_version+1 WHERE"
                + " email=?",
            encoder.encode(adminPassword),
            email);
    } else if (db.queryForObject("SELECT COUNT(*) FROM staff", Integer.class) == 0) {
      if (production)
        throw new IllegalStateException("Initial ADMIN_EMAIL and ADMIN_PASSWORD are required.");
      LoggerFactory.getLogger(getClass())
          .warn("No staff account exists. Set ADMIN_EMAIL and ADMIN_PASSWORD, then restart.");
    }
  }

  private synchronized boolean allowed(String key, int limit) {
    long now = System.currentTimeMillis();
    attempts.entrySet().removeIf(e -> now - e.getValue().start > 900_000);
    if (attempts.size() > 10000) return false;
    Window w = attempts.get(key);
    if (w == null) w = new Window(now, 0);
    attempts.put(key, new Window(w.start, w.count + 1));
    return w.count < limit;
  }

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  ResponseEntity<?> me() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof StaffIdentity i)
      return ResponseEntity.ok(Map.of("email", i.email));
    return ResponseEntity.status(401).body(Map.of("message", "Please sign in."));
  }

  @PostMapping("/login")
  ResponseEntity<?> login(
      @Valid @RequestBody Login input, HttpServletRequest req, HttpServletResponse res) {
    String email = input.email.trim().toLowerCase(Locale.ROOT);
    if (!allowed("login:" + req.getRemoteAddr(), 30) || !allowed("account:" + email, 10))
      return ResponseEntity.status(429)
          .body(Map.of("message", "Too many attempts. Try again in 15 minutes."));
    var rows =
        db.queryForList("SELECT password_hash,credential_version FROM staff WHERE email=?", email);
    String hash = rows.isEmpty() ? dummyHash : (String) rows.get(0).get("password_hash");
    if (!encoder.matches(input.password, hash) || rows.isEmpty())
      return ResponseEntity.status(401).body(Map.of("message", "Email or password is incorrect."));
    attempts.remove("account:" + email);
    req.getSession();
    req.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new StaffIdentity(email, ((Number) rows.get(0).get("credential_version")).intValue()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, req, res);
    return ResponseEntity.ok(Map.of("email", email));
  }

  @PostMapping("/logout")
  Map<String, String> logout(HttpServletRequest req) {
    var session = req.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("message", "Signed out.");
  }
}
