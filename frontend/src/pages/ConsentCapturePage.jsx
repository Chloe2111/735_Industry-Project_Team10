import { useState } from "react";
import ConsentCaptureForm from "../components/ConsentCapture/ConsentCaptureForm";
import { submitConsent } from "../services/consentApi";
import { useToast } from "../components/Toast/ToastProvider";

/**
 * ConsentCapturePage
 *
 * Sub Task 22.4: success and error handling.
 *
 * Wires the form (22.1 + 22.2) to the API call (22.3) and shows the
 * result via the project's real toast system. useToast() returns
 * { success, error, info, dismiss } — matched exactly to
 * ToastProvider.jsx, not guessed.
 *
 * apiClient's ApiError already carries a friendly, ready-to-display
 * `.message` (network issue, backend validation message, or a generic
 * fallback) — so this page just shows err.message directly rather
 * than building its own error text.
 */
export default function ConsentCapturePage() {
  const [submitting, setSubmitting] = useState(false);
  const [formKey, setFormKey] = useState(0); // used to reset the form after success
  const toast = useToast();

  async function handleSubmit(payload) {
    setSubmitting(true);
    try {
      await submitConsent(payload);
      toast.success("Consent recorded. Thank you.");
      setFormKey((k) => k + 1); // remounts the form with fresh empty state
    } catch (err) {
      console.error("Consent submission failed:", err);
      toast.error(err.message || "Something went wrong saving this. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="consent-capture-page">
      <ConsentCaptureForm key={formKey} onSubmit={handleSubmit} submitting={submitting} />
    </div>
  );
}
