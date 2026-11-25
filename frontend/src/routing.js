/** Keep historical hash links working alongside directly addressable legal pages. */
export function legalPage(location) {
  const hashPage = location.hash.slice(1);
  if (hashPage === "privacy" || hashPage === "terms") return hashPage;
  const pathPage = location.pathname.replace(/^\/+|\/+$/g, "");
  return pathPage === "privacy" || pathPage === "terms" ? pathPage : "";
}
