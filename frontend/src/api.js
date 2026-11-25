let csrf;
export async function refreshCsrf() {
  const response = await fetch("/api/auth/csrf", {
    credentials: "same-origin",
  });
  if (!response.ok)
    throw new Error("Unable to establish a secure connection. Please retry.");
  csrf = await response.json();
}
export async function api(path, method = "GET", body) {
  if (method !== "GET" && !csrf) await refreshCsrf();
  const response = await fetch(`/api${path}`, {
    method,
    credentials: "same-origin",
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(method !== "GET" ? { [csrf.headerName]: csrf.token } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const data =
    response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    const error = new Error(
      data?.message ||
        (response.status === 401
          ? "Your session has expired. Please sign in again."
          : "The request could not be completed. Please retry."),
    );
    error.status = response.status;
    error.errors = data?.errors;
    throw error;
  }
  return data;
}
