import { describe, test, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import ConsentCaptureForm from "../ConsentCaptureForm";

describe("ConsentCaptureForm validation (Sub Task 22.2)", () => {
  test("does not submit if group name is empty", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<ConsentCaptureForm onSubmit={onSubmit} />);

    await user.click(screen.getByText("Submit"));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText(/enter which group/i)).toBeInTheDocument();
  });

  test("does not submit if no tier is selected", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<ConsentCaptureForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/which group/i), "Youth Group A");
    await user.click(screen.getByText("Submit"));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText(/choose how sensitive/i)).toBeInTheDocument();
  });

  test("does not submit if consent checkbox is unchecked", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<ConsentCaptureForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/which group/i), "Youth Group A");
    await user.click(screen.getByLabelText(/general feedback/i));
    await user.click(screen.getByText("Submit"));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText(/consent must be given/i)).toBeInTheDocument();
  });

  test("submits with correct payload when everything is valid", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<ConsentCaptureForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/which group/i), "Youth Group A");
    await user.click(screen.getByLabelText(/general feedback/i));
    await user.click(screen.getByLabelText(/I understand how this information/i));
    await user.click(screen.getByText("Submit"));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith({
      participantName: null,
      groupName: "Youth Group A",
      tier: 1,
      consentGiven: true,
      notes: null,
    });
  });

  test("participant name is optional and can be left blank", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<ConsentCaptureForm onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/which group/i), "Elders Circle");
    await user.click(screen.getByLabelText(/culturally restricted/i));
    await user.click(screen.getByLabelText(/I understand how this information/i));
    await user.click(screen.getByText("Submit"));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ participantName: null, tier: 3 })
    );
  });

  test("submit button is disabled while submitting", () => {
    render(<ConsentCaptureForm onSubmit={vi.fn()} submitting={true} />);
    expect(screen.getByText(/Submitting/i)).toBeDisabled();
  });
});
