import { useState } from "react";
import "./ConsentCaptureForm.css";

const TIER_OPTIONS = [
  {
    value: 1,
    label: "General feedback",
    description: "Nothing personal or identifying in what I shared.",
  },
  {
    value: 2,
    label: "Personal or sensitive",
    description: "Includes my name, a personal story, or identifying details.",
  },
  {
    value: 3,
    label: "Culturally restricted",
    description:
      "Includes cultural knowledge, sacred content, or anything that should never leave the community.",
  },
];

export default function ConsentCaptureForm({ onSubmit, submitting = false }) {
  const [participantName, setParticipantName] = useState("");
  const [groupName, setGroupName] = useState("");
  const [tier, setTier] = useState(null);
  const [consentGiven, setConsentGiven] = useState(false);
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState({});

  function validate() {
    const nextErrors = {};

    if (!groupName.trim()) {
      nextErrors.groupName = "Please enter which group this relates to.";
    }

    if (tier === null) {
      nextErrors.tier = "Please choose how sensitive this information is.";
    }

    if (!consentGiven) {
      nextErrors.consentGiven = "Consent must be given before this can be submitted.";
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  function handleSubmit(e) {
    e.preventDefault();
    if (!validate()) return;

    onSubmit({
      participantName: participantName.trim() || null,
      groupName: groupName.trim(),
      tier,
      consentGiven,
      notes: notes.trim() || null,
    });
  }

  return (
    <form className="consent-form" onSubmit={handleSubmit} noValidate>
      <h2 className="consent-form__title">Before we record this</h2>
      <p className="consent-form__intro">
        This tells us how your information should be handled. You&rsquo;re in control of this,
        not us.
      </p>

      <div className="consent-form__field">
        <label htmlFor="participantName">Your name (optional)</label>
        <input
          id="participantName"
          type="text"
          value={participantName}
          onChange={(e) => setParticipantName(e.target.value)}
          placeholder="You can leave this blank to stay anonymous"
        />
      </div>

      <div className="consent-form__field">
        <label htmlFor="groupName">Which group is this for? *</label>
        <input
          id="groupName"
          type="text"
          value={groupName}
          onChange={(e) => setGroupName(e.target.value)}
          aria-invalid={Boolean(errors.groupName)}
          aria-describedby={errors.groupName ? "groupName-error" : undefined}
        />
        {errors.groupName && (
          <p className="consent-form__error" id="groupName-error" role="alert">
            {errors.groupName}
          </p>
        )}
      </div>

      <fieldset className="consent-form__field">
        <legend>How sensitive is this information? *</legend>
        {TIER_OPTIONS.map((option) => (
          <label key={option.value} className="consent-form__tier-option">
            <input
              type="radio"
              name="tier"
              value={option.value}
              checked={tier === option.value}
              onChange={() => setTier(option.value)}
            />
            <span>
              <strong>{option.label}</strong>
              <span className="consent-form__tier-desc">{option.description}</span>
            </span>
          </label>
        ))}
        {errors.tier && (
          <p className="consent-form__error" role="alert">
            {errors.tier}
          </p>
        )}
      </fieldset>

      <div className="consent-form__field">
        <label htmlFor="notes">Anything else you&rsquo;d like us to know? (optional)</label>
        <textarea id="notes" value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} />
      </div>

      <div className="consent-form__field consent-form__consent-check">
        <label>
          <input
            type="checkbox"
            checked={consentGiven}
            onChange={(e) => setConsentGiven(e.target.checked)}
            aria-invalid={Boolean(errors.consentGiven)}
          />
          <span>
            I understand how this information will be used and I consent to it being recorded.
          </span>
        </label>
        {errors.consentGiven && (
          <p className="consent-form__error" role="alert">
            {errors.consentGiven}
          </p>
        )}
      </div>

      <button type="submit" className="consent-form__submit" disabled={submitting}>
        {submitting ? "Submitting\u2026" : "Submit"}
      </button>
    </form>
  );
}
