package in.dscribe.admissions;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebController {
  private final JdbcTemplate db;

  WebController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping({"/", "/privacy", "/terms", "/login"})
  String index() {
    return "forward:/index.html";
  }

  @GetMapping("/api/health")
  @ResponseBody
  Map<String, String> health() {
    db.queryForObject("SELECT 1", Integer.class);
    return Map.of("status", "ok");
  }
}
