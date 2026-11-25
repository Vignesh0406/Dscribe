import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { api, refreshCsrf } from "./api";
import { validatePatient } from "./validation";
import { legalPage } from "./routing";
import "./styles.css";

function Icon({ name, size = 20 }) {
  const paths = {
    cross: <path d="M9 3h6v6h6v6h-6v6H9v-6H3V9h6z" />,
    list: (
      <>
        <rect x="4" y="3" width="16" height="18" rx="1" />
        <path d="M8 8h8M8 12h8M8 16h5" />
      </>
    ),
    search: (
      <>
        <circle cx="10" cy="10" r="6" />
        <path d="m15 15 5 5" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    close: <path d="m6 6 12 12M18 6 6 18" />,
    edit: (
      <>
        <path d="m15 4 5 5-10 10-6 1 1-6zM13 6l5 5" />
      </>
    ),
    trash: (
      <>
        <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5" />
      </>
    ),
    exit: (
      <>
        <path d="M10 4H4v16h6M10 12h11m-4-4 4 4-4 4" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] || paths.list}
    </svg>
  );
}
function Brand() {
  return (
    <div className="brand">
      <span className="brand-symbol">
        <Icon name="cross" size={25} />
      </span>
      <span>
        Admission<span className="brand-sub">PATIENT REGISTER</span>
      </span>
    </div>
  );
}
function Footer() {
  return (
    <footer>
      <span>Hospital staff workspace</span>
      <nav aria-label="Legal">
        <a href="/privacy">Privacy policy</a>
        <a href="/terms">Terms &amp; conditions</a>
      </nav>
    </footer>
  );
}
function Notice({ children, type = "error" }) {
  return children ? (
    <div
      className={`notice ${type}`}
      role={type === "error" ? "alert" : "status"}
    >
      {children}
    </div>
  ) : null;
}

function Dialog({ title, children, onClose, wide = false }) {
  const ref = useRef(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const previous = document.activeElement;
    const dialog = ref.current;
    dialog.showModal();
    const onCancel = (e) => {
      e.preventDefault();
      closeRef.current();
    };
    dialog.addEventListener("cancel", onCancel);
    return () => {
      dialog.close();
      dialog.removeEventListener("cancel", onCancel);
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      className={wide ? "wide" : ""}
      ref={ref}
      aria-labelledby="dialog-title"
    >
      <div className="dialog-heading">
        <h2 id="dialog-title">{title}</h2>
        <button
          type="button"
          className="icon-button"
          aria-label="Close dialog"
          onClick={onClose}
        >
          <Icon name="close" />
        </button>
      </div>
      {children}
    </dialog>
  );
}

function Auth({ onLogin }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const user = await api("/auth/login", "POST", {
        email: form.get("email"),
        password: form.get("password"),
      });
      await refreshCsrf();
      onLogin(user);
    } catch (error) {
      setError(error.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-layout">
      <aside className="auth-aside">
        <Brand />
        <div>
          <p className="eyebrow">HOSPITAL OPERATIONS</p>
          <h1>Patient admission management</h1>
          <p>
            Manage admissions, keep patient details current, and record
            discharges in one staff workspace.
          </p>
        </div>
        <p className="aside-note">For authorized hospital staff only.</p>
      </aside>
      <main className="auth-main">
        <section className="auth-form">
          <p className="eyebrow">STAFF ACCESS</p>
          <h1>Sign in to your workspace</h1>
          <p className="muted">
            Use your staff email and password to continue.
          </p>
          <Notice>{error}</Notice>
          <form onSubmit={submit}>
            <label>
              Email address
              <input
                name="email"
                type="email"
                required
                autoComplete="username"
                placeholder="you@hospital.org"
                maxLength={254}
              />
            </label>
            <label>
              Password
              <input
                name="password"
                type="password"
                required
                maxLength={128}
                autoComplete="current-password"
              />
            </label>
            <button className="primary full-width" disabled={busy}>
              {busy ? "Please wait..." : "Sign in"}
            </button>
          </form>
          <p className="auth-help">
            Need an account or help signing in? Contact the person managing this
            installation. Public registration is not available.
          </p>
        </section>
        <Footer />
      </main>
    </div>
  );
}

const emptyPatient = {
  name: "",
  age: "",
  gender: "",
  ward: "",
  department: "",
  doctor: "",
  admissionDate: "",
  status: "ADMITTED",
};
function PatientForm({ patient, options, onClose, onSaved }) {
  const [values, setValues] = useState(
    patient || { ...emptyPatient, admissionDate: options.today },
  );
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  function field(name) {
    return {
      name,
      id: name,
      value: values[name],
      onChange: (e) => {
        setValues({ ...values, [name]: e.target.value });
        setErrors({ ...errors, [name]: undefined });
      },
      "aria-invalid": !!errors[name],
      "aria-describedby": errors[name] ? `${name}-error` : undefined,
    };
  }
  function hint(name) {
    return (
      errors[name] && (
        <span className="field-error" id={`${name}-error`}>
          {errors[name]}
        </span>
      )
    );
  }
  async function submit(event) {
    event.preventDefault();
    const issues = validatePatient(values, options);
    setErrors(issues);
    if (Object.keys(issues).length) {
      setTimeout(
        () => document.querySelector('[aria-invalid="true"]')?.focus(),
        0,
      );
      return;
    }
    setBusy(true);
    setError("");
    try {
      const { id, ...body } = values;
      await api(id ? `/patients/${id}` : "/patients", id ? "PUT" : "POST", {
        ...body,
        name: body.name.trim(),
        age: Number(body.age),
      });
      onSaved(patient ? "Patient details updated." : "Patient admitted.");
    } catch (e) {
      setError(e.message);
      setErrors(e.errors || {});
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={patient ? "Edit patient" : "Add a patient"}
      onClose={() => !busy && onClose()}
      wide
    >
      <p className="dialog-description">
        {patient
          ? "Update the patient record below."
          : "Create an admission record. All fields are required."}
      </p>
      <Notice>{error}</Notice>
      <form onSubmit={submit} noValidate>
        <div className="form-grid">
          <label className="span-two" htmlFor="name">
            Full name
            <input
              {...field("name")}
              autoComplete="off"
              maxLength={120}
              autoFocus
              required
              placeholder="Patient’s full name"
            />
            {hint("name")}
          </label>
          <label htmlFor="age">
            Age
            <input
              {...field("age")}
              type="number"
              min="0"
              max="120"
              step="1"
              required
              placeholder="0–120"
            />
            {hint("age")}
          </label>
          {[
            ["gender", "Gender", "genders"],
            ["ward", "Ward", "wards"],
            ["department", "Department", "departments"],
            ["doctor", "Doctor", "doctors"],
          ].map(([name, label, list]) => (
            <label key={name} htmlFor={name}>
              {label}
              <select {...field(name)} required>
                <option value="">Select {label.toLowerCase()}</option>
                {options[list].map((item) => (
                  <option key={item}>{item}</option>
                ))}
              </select>
              {hint(name)}
            </label>
          ))}
          <label htmlFor="admissionDate">
            Admission date
            <input
              {...field("admissionDate")}
              type="date"
              max={options.today}
              required
            />
            {hint("admissionDate")}
            <span className="field-hint">Dates use Indian Standard Time.</span>
          </label>
          {patient && (
            <label htmlFor="status">
              Status
              <select {...field("status")}>
                <option value="ADMITTED">Admitted</option>
                <option value="DISCHARGED">Discharged</option>
              </select>
              {hint("status")}
            </label>
          )}
        </div>
        <div className="dialog-actions">
          <button
            type="button"
            className="secondary"
            onClick={onClose}
            disabled={busy}
          >
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Saving…" : patient ? "Save changes" : "Add patient"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

function Workspace({ user, onLogout }) {
  const [options, setOptions] = useState(null);
  const [patients, setPatients] = useState([]);
  const [filters, setFilters] = useState({
    search: "",
    ward: "",
    department: "",
    doctor: "",
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [optionsError, setOptionsError] = useState("");
  const [message, setMessage] = useState("");
  const [editor, setEditor] = useState(null);
  const [action, setAction] = useState(null);
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const [status, setStatus] = useState("ALL");
  useEffect(() => {
    let active = true;
    api("/options")
      .then((data) => {
        if (active) {
          setOptions(data);
          setOptionsError("");
        }
      })
      .catch((e) => {
        if (active) {
          setOptionsError(e.message);
          if (e.status === 401) onLogout();
        }
      });
    return () => {
      active = false;
    };
  }, [revision]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    const timer = setTimeout(() => {
      const query = new URLSearchParams(
        Object.entries(filters).filter(([, value]) => value),
      );
      api(`/patients?${query}`)
        .then((data) => {
          if (active) {
            setPatients(data);
            setError("");
          }
        })
        .catch((e) => {
          if (active) {
            setError(e.message);
            if (e.status === 401) onLogout();
          }
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 200);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [filters, revision]);
  const visible = patients.filter(
    (patient) => status === "ALL" || patient.status === status,
  );
  const admitted = patients.filter(
    (patient) => patient.status === "ADMITTED",
  ).length;
  function saved(text) {
    setEditor(null);
    setAction(null);
    setMessage(text);
    setRevision((r) => r + 1);
  }
  async function confirm() {
    setBusy(true);
    setError("");
    try {
      await api(
        `/patients/${action.patient.id}${action.type === "discharge" ? "/discharge" : ""}`,
        action.type === "discharge" ? "PATCH" : "DELETE",
      );
      saved(
        action.type === "discharge"
          ? "Patient marked as discharged."
          : "Patient record deleted.",
      );
    } catch (e) {
      setError(e.message);
      setAction(null);
    } finally {
      setBusy(false);
    }
  }
  async function logout() {
    setBusy(true);
    try {
      await api("/auth/logout", "POST");
      await refreshCsrf();
      onLogout();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="app-layout">
      <aside className="sidebar">
        <Brand />
        <div className="sidebar-section">WORKSPACE</div>
        <a className="nav-item active" href="#">
          <Icon name="list" />
          Patient register
        </a>
        <div className="sidebar-bottom">
          <span className="eyebrow">SIGNED IN AS</span>
          <span className="staff-email" title={user.email}>
            {user.email}
          </span>
          <button className="signout" onClick={logout} disabled={busy}>
            <Icon name="exit" />
            Sign out
          </button>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            Hospital operations <span className="breadcrumb">/</span> Patient
            register
          </span>
          <span className="workspace-label">Staff workspace</span>
        </header>
        <main className="workspace-main">
          <div className="page-heading">
            <div>
              <p className="eyebrow">ADMISSIONS</p>
              <h1>Patient register</h1>
              <p className="muted">
                Manage patient records from admission to discharge.
              </p>
            </div>
            <button
              className="primary"
              onClick={() => setEditor("new")}
              disabled={!options}
            >
              <Icon name="plus" />
              Add patient
            </button>
          </div>
          <Notice>{error}</Notice>
          <Notice type="success">{message}</Notice>
          {optionsError && (
            <Notice>
              {optionsError}{" "}
              <button
                className="text-button"
                onClick={() => setRevision((value) => value + 1)}
              >
                Retry loading form options
              </button>
            </Notice>
          )}
          <section className="register" aria-label="Patient records">
            <div className="register-heading">
              <div className="tabs" role="group" aria-label="Filter by status">
                {[
                  ["ALL", "All patients", patients.length],
                  ["ADMITTED", "Admitted", admitted],
                  ["DISCHARGED", "Discharged", patients.length - admitted],
                ].map(([value, label, count]) => (
                  <button
                    key={value}
                    onClick={() => setStatus(value)}
                    className={status === value ? "selected" : ""}
                    aria-pressed={status === value}
                  >
                    {label}
                    <span>{count}</span>
                  </button>
                ))}
              </div>
              <span className="record-note">
                {Object.values(filters).some(Boolean)
                  ? "Matching records"
                  : "All records"}
              </span>
            </div>
            <div className="filter-grid">
              <label className="search-label">
                <span>Search patients</span>
                <span className="search-control">
                  <Icon name="search" />
                  <input
                    type="search"
                    value={filters.search}
                    onChange={(e) =>
                      setFilters({ ...filters, search: e.target.value })
                    }
                    placeholder="Search by patient name"
                    maxLength={120}
                  />
                </span>
              </label>
              {[
                ["ward", "Ward", "wards"],
                ["department", "Department", "departments"],
                ["doctor", "Doctor", "doctors"],
              ].map(([name, label, list]) => (
                <label key={name}>
                  {label}
                  <select
                    value={filters[name]}
                    onChange={(e) =>
                      setFilters({ ...filters, [name]: e.target.value })
                    }
                  >
                    <option value="">
                      All{" "}
                      {name === "doctor"
                        ? "doctors"
                        : name === "ward"
                          ? "wards"
                          : "departments"}
                    </option>
                    {options?.[list].map((value) => (
                      <option key={value}>{value}</option>
                    ))}
                  </select>
                </label>
              ))}
            </div>
            <div className="results-line">
              <span aria-live="polite">
                {loading
                  ? "Loading patient records…"
                  : `${visible.length} patient${visible.length === 1 ? "" : "s"}`}
              </span>
              {(Object.values(filters).some(Boolean) || status !== "ALL") && (
                <button
                  className="text-button"
                  onClick={() => {
                    setFilters({
                      search: "",
                      ward: "",
                      department: "",
                      doctor: "",
                    });
                    setStatus("ALL");
                  }}
                >
                  Clear filters
                </button>
              )}
            </div>
            {loading ? (
              <div className="empty-state" role="status">
                <h2>Loading records</h2>
                <p>Fetching the latest patient information.</p>
              </div>
            ) : error ? (
              <div className="empty-state">
                <h2>Records could not be loaded</h2>
                <p>Check your connection and try again.</p>
                <button
                  className="secondary"
                  onClick={() => setRevision((r) => r + 1)}
                >
                  Try again
                </button>
              </div>
            ) : !visible.length ? (
              <div className="empty-state">
                <span className="empty-icon">
                  <Icon name="list" size={30} />
                </span>
                <h2>
                  {patients.length ||
                  Object.values(filters).some(Boolean) ||
                  status !== "ALL"
                    ? "No matching patients"
                    : "Your register is ready"}
                </h2>
                <p>
                  {Object.values(filters).some(Boolean) || status !== "ALL"
                    ? "Try a different name or change your filters."
                    : "Add your first patient to begin recording admissions."}
                </p>
                {!Object.values(filters).some(Boolean) && status === "ALL" && (
                  <button
                    className="secondary"
                    onClick={() => setEditor("new")}
                    disabled={!options}
                  >
                    <Icon name="plus" />
                    Add a patient
                  </button>
                )}
              </div>
            ) : (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Patient</th>
                      <th>Ward / Department</th>
                      <th>Doctor</th>
                      <th>Admission date</th>
                      <th>Status</th>
                      <th className="actions-heading">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((patient) => (
                      <tr key={patient.id}>
                        <td>
                          <strong>{patient.name}</strong>
                          <span className="cell-detail">
                            {patient.age} years · {patient.gender}
                          </span>
                        </td>
                        <td>
                          {patient.ward}
                          <span className="cell-detail">
                            {patient.department}
                          </span>
                        </td>
                        <td>{patient.doctor}</td>
                        <td className="nowrap">
                          {new Intl.DateTimeFormat("en-IN", {
                            day: "2-digit",
                            month: "short",
                            year: "numeric",
                            timeZone: "UTC",
                          }).format(
                            new Date(`${patient.admissionDate}T00:00:00Z`),
                          )}
                        </td>
                        <td>
                          <span
                            className={`status ${patient.status.toLowerCase()}`}
                          >
                            {patient.status === "ADMITTED"
                              ? "Admitted"
                              : "Discharged"}
                          </span>
                        </td>
                        <td>
                          <div className="row-actions">
                            <button
                              className="icon-button"
                              onClick={() => setEditor(patient)}
                              aria-label={`Edit ${patient.name}`}
                              title="Edit patient"
                            >
                              <Icon name="edit" />
                            </button>
                            {patient.status === "ADMITTED" && (
                              <button
                                className="icon-button discharge-button"
                                onClick={() =>
                                  setAction({ type: "discharge", patient })
                                }
                                aria-label={`Discharge ${patient.name}`}
                                title="Mark as discharged"
                              >
                                <Icon name="check" />
                              </button>
                            )}
                            <button
                              className="icon-button danger-icon"
                              onClick={() =>
                                setAction({ type: "delete", patient })
                              }
                              aria-label={`Delete ${patient.name}`}
                              title="Delete patient"
                            >
                              <Icon name="trash" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="register-footer">
              Patient information is confidential. Access only for authorized
              work.
            </div>
          </section>
        </main>
        <Footer />
      </div>
      {editor && options && (
        <PatientForm
          patient={editor === "new" ? null : editor}
          options={options}
          onClose={() => setEditor(null)}
          onSaved={saved}
        />
      )}
      {action && (
        <Dialog
          title={
            action.type === "delete"
              ? "Delete this patient?"
              : "Record patient discharge?"
          }
          onClose={() => !busy && setAction(null)}
        >
          <p className="confirm-copy">
            {action.type === "delete" ? (
              <>
                The record for <strong>{action.patient.name}</strong> will be
                permanently deleted. This cannot be undone.
              </>
            ) : (
              <>
                Mark <strong>{action.patient.name}</strong> as discharged? Their
                record will remain in the register.
              </>
            )}
          </p>
          <div className="dialog-actions">
            <button
              className="secondary"
              onClick={() => setAction(null)}
              disabled={busy}
            >
              Cancel
            </button>
            <button
              className={action.type === "delete" ? "danger" : "primary"}
              disabled={busy}
              onClick={confirm}
            >
              {busy
                ? "Saving…"
                : action.type === "delete"
                  ? "Delete patient"
                  : "Confirm discharge"}
            </button>
          </div>
        </Dialog>
      )}
    </div>
  );
}

function Legal({ type }) {
  const privacy = type === "privacy";
  return (
    <div className="legal-layout">
      <header>
        <a className="brand-link" href="/">
          <Brand />
        </a>
        <a href="/">Back to workspace</a>
      </header>
      <main>
        <p className="eyebrow">WORKSPACE INFORMATION</p>
        <h1>{privacy ? "Privacy policy" : "Terms & conditions"}</h1>
        <p className="legal-intro">
          This admission-management application is an internship demonstration.
          It is not a certified clinical system. Use fictional patient data
          only.
        </p>
        {privacy ? (
          <>
            <h2>Information stored</h2>
            <p>
              The application stores patient names, ages, genders, wards,
              departments, assigned doctors, admission dates and admission
              statuses. Staff email addresses and hashed passwords are stored
              for account access.
            </p>
            <h2>How information is used</h2>
            <p>
              Records support patient admission management by authorized staff.
              Email addresses identify staff accounts for sign-in. The hosting
              service processes the information required to run this workspace.
              This application does not send emails.
            </p>
            <h2>Sessions and local storage</h2>
            <p>
              An essential session cookie keeps staff signed in. Security tokens
              protect form submissions. This application does not include
              advertising, customer tracking or third-party analytics.
            </p>
            <h2>Retention and deletion</h2>
            <p>
              Patient records remain until an authorized staff member deletes
              them. Hosting backups may retain older copies according to the
              hosting provider’s settings. Contact the installation
              administrator about account deletion or access to stored
              information.
            </p>
            <h2>Before using real patient information</h2>
            <p>
              The operator must establish appropriate privacy notices, retention
              and backup policies, access controls, legal authority and any
              required security agreements. This demonstration does not
              establish those safeguards on its own.
            </p>
          </>
        ) : (
          <>
            <h2>Authorized use</h2>
            <p>
              Access is restricted to staff authorized by the installation
              administrator. Do not share passwords or access patient
              information beyond your responsibilities. Do not upload real
              patient information to this demonstration.
            </p>
            <h2>Record accuracy</h2>
            <p>
              Users are responsible for checking entered information. This
              application is not intended for diagnosis, treatment decisions,
              emergency care, medication management or billing.
            </p>
            <h2>Availability and changes</h2>
            <p>
              This demonstration is provided for evaluation without a guarantee
              of continuous availability or fitness for clinical use. Records
              can be edited or permanently deleted by authorized users. Maintain
              independent backups if retaining demonstration data matters.
            </p>
            <h2>Administration</h2>
            <p>
              The installation administrator manages accounts, hosting, email
              delivery, retention and access. Contact that administrator for
              support or questions about these terms.
            </p>
            <h2>Deployment responsibility</h2>
            <p>
              A hospital deployment requires a separate operational and legal
              review, suitable policies and security controls. These terms do
              not replace that review.
            </p>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}

function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [connectionError, setConnectionError] = useState("");
  const [page, setPage] = useState(() => legalPage(window.location));
  useEffect(() => {
    const change = () => setPage(legalPage(window.location));
    window.addEventListener("hashchange", change);
    return () => window.removeEventListener("hashchange", change);
  }, []);
  async function initialize() {
    setLoading(true);
    setConnectionError("");
    try {
      await refreshCsrf();
      try {
        setUser(await api("/auth/me"));
      } catch (e) {
        if (e.status !== 401) throw e;
      }
    } catch (e) {
      setConnectionError(e.message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    initialize();
  }, []);
  if (page === "privacy" || page === "terms") return <Legal type={page} />;
  if (loading)
    return (
      <main className="connection-state">
        <Brand />
        <p role="status">Connecting to the staff workspace…</p>
      </main>
    );
  if (connectionError)
    return (
      <main className="connection-state">
        <Brand />
        <h1>Unable to connect</h1>
        <Notice>{connectionError}</Notice>
        <button className="primary" onClick={initialize}>
          Try again
        </button>
        <Footer />
      </main>
    );
  return user ? (
    <Workspace user={user} onLogout={() => setUser(null)} />
  ) : (
    <Auth onLogin={setUser} />
  );
}
createRoot(document.getElementById("root")).render(<App />);
