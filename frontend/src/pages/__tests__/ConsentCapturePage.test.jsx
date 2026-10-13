import { describe, test, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ToastProvider } from "../../components/Toast/ToastProvider";
import ConsentCapturePage from "../ConsentCapturePage";

/**
 * Integration test for Sub Task 22.3 + 22.4: confirms the page
 * actually talks to apiClient correctly (right path, right method)
 * and shows the right toast on both success and failure — using the
 * REAL ToastProvider, not a mock of it, so this proves the actual
 * wiring works end to end, not just each piece in isolation.
 */

function fillAndSubmitValidForm(user) {
  return (async () => {
    await user.type(screen.getByLabelText(/which group/i), "Youth Group A");
    await user.click(screen.getByLabelText(/general feedback/i));
    await user.click(screen.getByLabelText(/I understand how this information/i));
    await user.click(screen.getByText("Submit"));
  })();
}

describe("ConsentCapturePage integration (Sub Task 22.3 + 22.4)", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  test("calls the correct endpoint with POST and the form payload", async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      headers: { get: () => "application/json" },
      json: async () => ({ id: "abc123", groupName: "Youth Group A", tier: 1 }),
    });

    const user = userEvent.setup();
    render(
      <ToastProvider>
        <ConsentCapturePage />
      </ToastProvider>
    );

    await fillAndSubmitValidForm(user);

    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));
    const [url, options] = global.fetch.mock.calls[0];

    // Confirms the /api/api/consent double-prefix bug is NOT present
    expect(url).toBe("/api/consent");
    expect(options.method).toBe("POST");
    expect(JSON.parse(options.body)).toEqual({
      participantName: null,
      groupName: "Youth Group A",
      tier: 1,
      consentGiven: true,
      notes: null,
    });
  });

  test("shows a success toast and resets the form after a successful submit", async () => {
    global.fetch.mockResolvedValueOnce({
      ok: true,
      headers: { get: () => "application/json" },
      json: async () => ({ id: "abc123" }),
    });

    const user = userEvent.setup();
    render(
      <ToastProvider>
        <ConsentCapturePage />
      </ToastProvider>
    );

    await fillAndSubmitValidForm(user);

    expect(await screen.findByText("Consent recorded. Thank you.")).toBeInTheDocument();

    // Form should be reset — group name field empty again
    expect(screen.getByLabelText(/which group/i)).toHaveValue("");
  });

  test("shows the real backend error message in a toast on failure", async () => {
    global.fetch.mockResolvedValueOnce({
      ok: false,
      status: 400,
      headers: { get: () => "application/json" },
      json: async () => ({ message: "Consent must be given before this can be recorded." }),
    });

    const user = userEvent.setup();
    render(
      <ToastProvider>
        <ConsentCapturePage />
      </ToastProvider>
    );

    await fillAndSubmitValidForm(user);

    expect(
      await screen.findByText("Consent must be given before this can be recorded.")
    ).toBeInTheDocument();

    // Form should NOT be reset on failure — user shouldn't lose their input
    expect(screen.getByLabelText(/which group/i)).toHaveValue("Youth Group A");
  });

  test("shows the network error message when the request fails to reach the server", async () => {
    global.fetch.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const user = userEvent.setup();
    render(
      <ToastProvider>
        <ConsentCapturePage />
      </ToastProvider>
    );

    await fillAndSubmitValidForm(user);

    expect(
      await screen.findByText(/unable to reach the server/i)
    ).toBeInTheDocument();
  });
});
