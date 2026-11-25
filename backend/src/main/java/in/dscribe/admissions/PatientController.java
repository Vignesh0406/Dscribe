package in.dscribe.admissions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PatientController {
  static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Kolkata");
  static final List<String> GENDERS = List.of("Female", "Male", "Other", "Prefer not to say");
  static final List<String> WARDS =
      List.of("General Ward", "Private Ward", "ICU", "Emergency", "Pediatric Ward");
  static final List<String> DEPARTMENTS =
      List.of("General Medicine", "Cardiology", "Neurology", "Orthopedics", "Pediatrics");
  static final List<String> DOCTORS =
      List.of("Dr. Ananya Rao", "Dr. Arjun Mehta", "Dr. Kavya Nair", "Dr. Rohan Shah");

  record Patient(
      Long id,
      String name,
      int age,
      String gender,
      String ward,
      String department,
      String doctor,
      LocalDate admissionDate,
      String status) {}

  record Input(
      @NotBlank(message = "Name is required.")
          @Size(max = 120, message = "Name must be at most 120 characters.")
          @Pattern(
              regexp = "(?=.*\\p{L})[\\p{L}\\p{M} ]+",
              message = "Name must contain letters and spaces only.")
          String name,
      @NotNull(message = "Age is required.")
          @Min(value = 0, message = "Age must be between 0 and 120.")
          @Max(value = 120, message = "Age must be between 0 and 120.")
          Integer age,
      String gender,
      String ward,
      String department,
      String doctor,
      @NotNull(message = "Admission date is required.") LocalDate admissionDate,
      String status) {}

  private final JdbcTemplate db;

  PatientController(JdbcTemplate db) {
    this.db = db;
  }

  private static final RowMapper<Patient> MAPPER =
      (r, n) ->
          new Patient(
              r.getLong("id"),
              r.getString("name"),
              r.getInt("age"),
              r.getString("gender"),
              r.getString("ward"),
              r.getString("department"),
              r.getString("doctor"),
              r.getDate("admission_date").toLocalDate(),
              r.getString("status"));

  @GetMapping("/options")
  Map<String, Object> options() {
    return Map.of(
        "genders",
        GENDERS,
        "wards",
        WARDS,
        "departments",
        DEPARTMENTS,
        "doctors",
        DOCTORS,
        "today",
        LocalDate.now(HOSPITAL_ZONE).toString());
  }

  @GetMapping("/patients")
  List<Patient> list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String ward,
      @RequestParam(defaultValue = "") String department,
      @RequestParam(defaultValue = "") String doctor) {
    StringBuilder sql = new StringBuilder("SELECT * FROM patients WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (!search.isBlank()) {
      sql.append(" AND POSITION(? IN LOWER(name)) > 0");
      args.add(search.trim().toLowerCase(Locale.ROOT));
    }
    String[] columns = {"ward", "department", "doctor"};
    String[] values = {ward, department, doctor};
    for (int i = 0; i < columns.length; i++)
      if (!values[i].isBlank()) {
        sql.append(" AND ").append(columns[i]).append(" = ?");
        args.add(values[i]);
      }
    sql.append(" ORDER BY admission_date DESC,id DESC");
    return db.query(sql.toString(), MAPPER, args.toArray());
  }

  void validate(Input p) {
    Map<String, String> errors = new LinkedHashMap<>();
    if ((p.gender == null || !GENDERS.contains(p.gender)))
      errors.put("gender", "Select a valid gender.");
    if ((p.ward == null || !WARDS.contains(p.ward))) errors.put("ward", "Select a valid ward.");
    if ((p.department == null || !DEPARTMENTS.contains(p.department)))
      errors.put("department", "Select a valid department.");
    if ((p.doctor == null || !DOCTORS.contains(p.doctor)))
      errors.put("doctor", "Select a valid doctor.");
    if (p.admissionDate != null && p.admissionDate.isAfter(LocalDate.now(HOSPITAL_ZONE)))
      errors.put("admissionDate", "Admission date cannot be in the future (IST).");
    if ((p.status == null || !List.of("ADMITTED", "DISCHARGED").contains(p.status)))
      errors.put("status", "Select Admitted or Discharged.");
    if (!errors.isEmpty()) throw new ApiErrors.Invalid(errors);
  }

  Patient get(long id) {
    return db.query("SELECT * FROM patients WHERE id=?", MAPPER, id).stream()
        .findFirst()
        .orElseThrow(ApiErrors.Missing::new);
  }

  @PostMapping("/patients")
  @ResponseStatus(HttpStatus.CREATED)
  Patient add(@Valid @RequestBody Input p) {
    validate(p);
    GeneratedKeyHolder key = new GeneratedKeyHolder();
    db.update(
        c -> {
          var s =
              c.prepareStatement(
                  "INSERT INTO"
                      + " patients(name,age,gender,ward,department,doctor,admission_date,status)"
                      + " VALUES (?,?,?,?,?,?,?,?)",
                  new String[] {"id"});
          s.setString(1, p.name.trim());
          s.setInt(2, p.age);
          s.setString(3, p.gender);
          s.setString(4, p.ward);
          s.setString(5, p.department);
          s.setString(6, p.doctor);
          s.setObject(7, p.admissionDate);
          s.setString(8, p.status);
          return s;
        },
        key);
    return get(Objects.requireNonNull(key.getKey()).longValue());
  }

  @PutMapping("/patients/{id}")
  Patient edit(@PathVariable long id, @Valid @RequestBody Input p) {
    validate(p);
    if (db.update(
            "UPDATE patients SET"
                + " name=?,age=?,gender=?,ward=?,department=?,doctor=?,admission_date=?,status=?"
                + " WHERE id=?",
            p.name.trim(),
            p.age,
            p.gender,
            p.ward,
            p.department,
            p.doctor,
            p.admissionDate,
            p.status,
            id)
        == 0) throw new ApiErrors.Missing();
    return get(id);
  }

  @PatchMapping("/patients/{id}/discharge")
  Patient discharge(@PathVariable long id) {
    if (db.update("UPDATE patients SET status='DISCHARGED' WHERE id=?", id) == 0)
      throw new ApiErrors.Missing();
    return get(id);
  }

  @DeleteMapping("/patients/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable long id) {
    if (db.update("DELETE FROM patients WHERE id=?", id) == 0) throw new ApiErrors.Missing();
  }
}
