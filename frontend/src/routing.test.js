import { describe, expect, it } from "vitest";
import { legalPage } from "./routing";

describe("legal page navigation", () => {
  it.each(["privacy", "terms"])("supports direct /%s links", (page) => {
    expect(legalPage({ pathname: `/${page}`, hash: "" })).toBe(page);
  });
  it.each(["privacy", "terms"])("preserves old #%s links", (page) => {
    expect(legalPage({ pathname: "/", hash: `#${page}` })).toBe(page);
  });
  it("does not mistake the password-reset path for a legal page", () => {
    expect(legalPage({ pathname: "/login", hash: "" })).toBe("");
  });
});
