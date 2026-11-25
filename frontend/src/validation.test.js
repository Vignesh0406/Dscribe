import { describe, it, expect } from "vitest";
import { validatePatient } from "./validation";
const options = {
  genders: ["Female"],
  wards: ["A"],
  departments: ["Medicine"],
  doctors: ["Dr Rao"],
  today: "2026-11-25",
};
const valid = {
  name: "Anita Rao",
  age: 32,
  gender: "Female",
  ward: "A",
  department: "Medicine",
  doctor: "Dr Rao",
  admissionDate: "2026-11-25",
  status: "ADMITTED",
};
describe("patient validation", () => {
  it("rejects names longer than 120 characters", () => {
    expect(
      validatePatient({ ...valid, name: "A".repeat(121) }, options).name,
    ).toBeTruthy();
  });
  it("accepts valid data and age zero", () => {
    expect(validatePatient(valid, options)).toEqual({});
    expect(validatePatient({ ...valid, age: 0 }, options)).toEqual({});
  });
  it("accepts Unicode letters", () =>
    expect(validatePatient({ ...valid, name: "अनिता राव" }, options)).toEqual(
      {},
    ));
  it.each(["", "123", "Jane Doe!", "   "])("rejects invalid name %s", (name) =>
    expect(validatePatient({ ...valid, name }, options).name).toBeTruthy(),
  );
  it.each(["", -1, 121, 1.5, "abc"])("rejects invalid age %s", (age) =>
    expect(validatePatient({ ...valid, age }, options).age).toBeTruthy(),
  );
  it("rejects unknown selections", () => {
    for (const field of ["gender", "ward", "department", "doctor", "status"])
      expect(
        validatePatient({ ...valid, [field]: "Other" }, options)[field],
      ).toBeTruthy();
  });
  it.each(["", "2026-11-26", "2026-02-30", "bad"])(
    "rejects invalid date %s",
    (admissionDate) =>
      expect(
        validatePatient({ ...valid, admissionDate }, options).admissionDate,
      ).toBeTruthy(),
  );
});
