CREATE TABLE patients (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    age INTEGER NOT NULL CHECK (age BETWEEN 0 AND 120),
    gender VARCHAR(30) NOT NULL,
    ward VARCHAR(60) NOT NULL,
    department VARCHAR(60) NOT NULL,
    doctor VARCHAR(80) NOT NULL,
    admission_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ADMITTED', 'DISCHARGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE staff (
    email VARCHAR(254) NOT NULL PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    credential_version INTEGER NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX patient_filters ON patients(ward, department, doctor);
