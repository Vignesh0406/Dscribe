export function validatePatient(patient, options) {
  const errors = {};
  if (
    !patient.name?.trim() ||
    patient.name.length > 120 ||
    !/^[\p{L}\p{M} ]+$/u.test(patient.name.trim()) ||
    !/\p{L}/u.test(patient.name)
  )
    errors.name = "Enter a name using letters and spaces only.";
  if (
    String(patient.age).trim() === "" ||
    !/^\d+$/.test(String(patient.age)) ||
    Number(patient.age) > 120
  )
    errors.age = "Enter a whole number from 0 to 120.";
  for (const [field, list] of [
    ["gender", "genders"],
    ["ward", "wards"],
    ["department", "departments"],
    ["doctor", "doctors"],
  ]) {
    if (!options[list]?.includes(patient[field]))
      errors[field] = `Select a ${field} from the list.`;
  }
  const date = patient.admissionDate;
  if (
    !date ||
    !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
    Number.isNaN(Date.parse(date)) ||
    new Date(`${date}T00:00:00Z`).toISOString().slice(0, 10) !== date ||
    date > options.today
  )
    errors.admissionDate = "Select a valid date that is not in the future.";
  if (!["ADMITTED", "DISCHARGED"].includes(patient.status))
    errors.status = "Select a valid status.";
  return errors;
}
