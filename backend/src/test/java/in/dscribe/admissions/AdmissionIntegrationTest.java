package in.dscribe.admissions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
      "spring.datasource.username=${TEST_DB_USERNAME:sa}",
      "spring.datasource.password=${TEST_DB_PASSWORD:}",
      "app.admin-email=staff@example.test",
      "app.admin-password=test-password-12345"
    })
@AutoConfigureMockMvc
class AdmissionIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired AuthController accounts;
  MockHttpSession session;

  @BeforeEach
  void login() throws Exception {
    db.update("DELETE FROM patients");
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        "{\"email\":\"staff@example.test\",\"password\":\"test-password-12345\"}"))
            .andExpect(status().isOk())
            .andReturn();
    session = (MockHttpSession) result.getRequest().getSession();
  }

  Map<String, Object> patient() {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("name", "Ananya Rao");
    p.put("age", 32);
    p.put("gender", "Female");
    p.put("ward", "General Ward");
    p.put("department", "Cardiology");
    p.put("doctor", "Dr. Ananya Rao");
    p.put("admissionDate", LocalDate.now(PatientController.HOSPITAL_ZONE).toString());
    p.put("status", "ADMITTED");
    return p;
  }

  long create(Map<String, Object> p) throws Exception {
    var result =
        mvc.perform(
                post("/api/patients")
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsBytes(p)))
            .andExpect(status().isCreated())
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  void entirePatientLifecycle() throws Exception {
    long id = create(patient());
    mvc.perform(get("/api/patients").session(session))
        .andExpect(jsonPath("$[0].name").value("Ananya Rao"));
    var p = patient();
    p.put("name", "Kavya Nair");
    mvc.perform(
            put("/api/patients/" + id)
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(p)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Kavya Nair"));
    mvc.perform(patch("/api/patients/" + id + "/discharge").session(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISCHARGED"));
    mvc.perform(delete("/api/patients/" + id).session(session).with(csrf()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/patients").session(session)).andExpect(content().json("[]"));
  }

  @Test
  void filtersCombineWithCaseInsensitiveNameSearch() throws Exception {
    create(patient());
    var other = patient();
    other.put("ward", "ICU");
    other.put("name", "Arjun Mehta");
    create(other);
    mvc.perform(
            get("/api/patients")
                .session(session)
                .param("search", "ANANYA")
                .param("ward", "General Ward")
                .param("department", "Cardiology")
                .param("doctor", "Dr. Ananya Rao"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/patients").session(session).param("search", "%"))
        .andExpect(content().json("[]"));
  }

  @Test
  void rejectsInvalidNames() throws Exception {
    for (String name : List.of("", "  ", "Patient123", "O'Neil", "<script>")) {
      var p = patient();
      p.put("name", name);
      reject(p, "name");
    }
  }

  @Test
  void ageBoundsAndRequired() throws Exception {
    for (Object age : Arrays.asList(-1, 121, null)) {
      var p = patient();
      p.put("age", age);
      reject(p, "age");
    }
    for (int age : List.of(0, 120)) {
      var p = patient();
      p.put("age", age);
      create(p);
    }
  }

  @Test
  void rejectsFractionalAgeAndBadDate() throws Exception {
    var p = patient();
    p.put("age", 1.5);
    mvc.perform(
            post("/api/patients")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(p)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
    p = patient();
    p.put("admissionDate", "2026-02-30");
    mvc.perform(
            post("/api/patients")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(p)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsStringAgeInsteadOfSilentlyCoercingIt() throws Exception {
    var p = patient();
    p.put("age", "32");
    mvc.perform(
            post("/api/patients")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(p)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void validatesAllDropdownsAndStatus() throws Exception {
    for (String key : List.of("gender", "ward", "department", "doctor", "status")) {
      var p = patient();
      p.put(key, "NOT_VALID");
      reject(p, key);
      p.put(key, null);
      reject(p, key);
    }
  }

  @Test
  void rejectsFutureAndMissingAdmissionDate() throws Exception {
    var p = patient();
    p.put("admissionDate", LocalDate.now(PatientController.HOSPITAL_ZONE).plusDays(1).toString());
    reject(p, "admissionDate");
    p.put("admissionDate", null);
    reject(p, "admissionDate");
  }

  @Test
  void editUsesSameValidation() throws Exception {
    long id = create(patient());
    var p = patient();
    p.put("age", 200);
    mvc.perform(
            put("/api/patients/" + id)
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(p)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.age").exists());
  }

  @Test
  void protectsRecordsAndRequiresCsrf() throws Exception {
    mvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/patients")
                .session(session)
                .contentType("application/json")
                .content(json.writeValueAsBytes(patient())))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString());
  }

  @Test
  void missingPatientHasClearError() throws Exception {
    mvc.perform(delete("/api/patients/999999").session(session).with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Patient not found. Refresh the register."));
  }

  @Test
  void staffCanSignOutAndCannotReuseTheSession() throws Exception {
    mvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("staff@example.test"));
    mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/api/patients").session(session)).andExpect(status().isUnauthorized());
  }

  @Test
  void administratorCanRotatePasswordWithoutEmailAndRevokeExistingSessions() throws Exception {
    String email = accounts.adminEmail;
    String originalPassword = accounts.adminPassword;
    boolean originalRotation = accounts.rotateAdminPassword;
    var original =
        db.queryForMap("SELECT password_hash,credential_version FROM staff WHERE email=?", email);
    try {
      accounts.adminPassword = "replacement-test-password-123";
      accounts.rotateAdminPassword = true;
      accounts.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
      mvc.perform(get("/api/patients").session(session)).andExpect(status().isUnauthorized());
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      json.writeValueAsBytes(Map.of("email", email, "password", originalPassword))))
          .andExpect(status().isUnauthorized());
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      json.writeValueAsBytes(
                          Map.of("email", email, "password", accounts.adminPassword))))
          .andExpect(status().isOk());
    } finally {
      db.update(
          "UPDATE staff SET password_hash=?,credential_version=? WHERE email=?",
          original.get("password_hash"),
          original.get("credential_version"),
          email);
      accounts.adminPassword = originalPassword;
      accounts.rotateAdminPassword = originalRotation;
    }
  }

  @Test
  void databaseMigrationRemovesObsoleteRecoveryTable() {
    org.junit.jupiter.api.Assertions.assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE"
                + " LOWER(table_name)='password_resets'",
            Integer.class));
  }

  @Test
  void textFieldsRejectWrongJsonTypesInsteadOfCoercingThemToNames() throws Exception {
    for (Object wrongName : List.of(true, false, 123, 12.5)) {
      var input = patient();
      input.put("name", wrongName);
      mvc.perform(
              post("/api/patients")
                  .session(session)
                  .with(csrf())
                  .contentType("application/json")
                  .content(json.writeValueAsBytes(input)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").isString());
    }
  }

  @Test
  void incorrectPasswordIsRejectedClearly() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"staff@example.test\",\"password\":\"incorrect-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
  }

  void reject(Map<String, Object> p, String key) throws Exception {
    mvc.perform(
            post("/api/patients")
                .session(session)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(p)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors." + key).exists());
  }
}
