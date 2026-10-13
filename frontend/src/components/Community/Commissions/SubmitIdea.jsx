import { useState } from "react";
import { CommunitySidebar } from "./OpenCommissions";
import CommunityHeader, { ChatButton } from "../CommunityHeader";

export default function SubmitIdea({ onNavigate }) {
  const [formData, setFormData] = useState({
    title: "",
    description: "",
    problem: "",
    beneficiaries: "",
    impact: "",
    confirmation: false,
  });

  const [errors, setErrors] = useState({});
  const [attachments, setAttachments] = useState([]);
  const [attachmentError, setAttachmentError] = useState("");

  const allowedExtensions = ["png", "jpg", "jpeg", "pdf", "mp4", "mov", "mp3", "wav", "m4a"];
  const maxFileSize = 10 * 1024 * 1024;

  function handleAttachments(event) {
    const selected = Array.from(event.target.files || []);
    const invalid = selected.find((file) => {
      const extension = file.name.split(".").pop()?.toLowerCase();
      return !allowedExtensions.includes(extension) || file.size > maxFileSize;
    });

    if (invalid) {
      setAttachmentError("Use PNG, JPG, JPEG, PDF, MP4, MOV, MP3, WAV or M4A files up to 10MB each.");
      event.target.value = "";
      return;
    }

    setAttachmentError("");
    setAttachments((current) => [...current, ...selected]);
    event.target.value = "";
  }

  function removeAttachment(index) {
    setAttachments((current) => current.filter((_, itemIndex) => itemIndex !== index));
  }

  function handleChange(event) {
    const { name, value, type, checked } = event.target;

    setFormData((current) => ({
      ...current,
      [name]: type === "checkbox" ? checked : value,
    }));

    setErrors((current) => ({
      ...current,
      [name]: "",
    }));
  }

  function validateForm() {
    const newErrors = {};

    if (!formData.title.trim()) {
      newErrors.title = "Idea title is required.";
    } else if (formData.title.trim().length < 3) {
      newErrors.title = "Idea title must be at least 3 characters.";
    }

    if (!formData.description.trim()) {
      newErrors.description = "Please describe your idea.";
    }

    if (!formData.problem.trim()) {
      newErrors.problem = "Please explain the problem your idea solves.";
    }

    if (!formData.beneficiaries.trim()) {
      newErrors.beneficiaries =
        "Please explain who would benefit from your idea.";
    }

    if (!formData.confirmation) {
      newErrors.confirmation =
        "Please confirm that your submission follows the community guidelines.";
    }

    setErrors(newErrors);

    return Object.keys(newErrors).length === 0;
  }

  function handleSubmit(event) {
    event.preventDefault();

    if (!validateForm()) {
      return;
    }

    onNavigate("success");
  }

  function handleDraft() {
    window.alert("Draft saved for this prototype.");
  }

  return (
    <div className="commission-page">
      <CommunityHeader active="commissions" onNavigate={onNavigate} />

      <div className="breadcrumb">
        Community Portal <span>›</span> Commissions <span>›</span> Skate Park
        Design Consultation <span>›</span> Submit an Idea
      </div>

      <main className="commission-layout">
        <CommunitySidebar onNavigate={onNavigate} />

        <form className="submission-layout" onSubmit={handleSubmit}>
          <section className="submission-card">
            <button
              className="back-link"
              type="button"
              onClick={() => onNavigate("details")}
            >
              ← Back to Commission Details
            </button>

            <h1>Submit an Idea</h1>
            <p className="organisation-large">
              Skate Park Design Consultation
            </p>

            <div className="submission-info">
              <strong>Share your ideas and help shape a better community.</strong>
              <p>
                Provide as much detail as possible so the organisation can fully
                understand your idea.
              </p>
            </div>

            <FormField
              label="Idea title"
              required
              error={errors.title}
              count={`${formData.title.length}/100`}
            >
              <input
                name="title"
                value={formData.title}
                onChange={handleChange}
                maxLength={100}
                placeholder="e.g. Inclusive Skate Zone"
              />
            </FormField>

            <FormField
              label="Describe your idea"
              required
              helper="Explain what you are proposing and how it would work. Recommended: 100–500 words."
              error={errors.description}
              count={`${formData.description.length}/500`}
            >
              <textarea
                name="description"
                value={formData.description}
                onChange={handleChange}
                maxLength={500}
                rows={6}
                placeholder="Describe your idea..."
              />
            </FormField>

            <FormField
              label="What problem does your idea solve?"
              required
              error={errors.problem}
              count={`${formData.problem.length}/300`}
            >
              <textarea
                name="problem"
                value={formData.problem}
                onChange={handleChange}
                maxLength={300}
                rows={4}
                placeholder="Explain the need or challenge..."
              />
            </FormField>

            <FormField
              label="Who would benefit from this idea?"
              required
              error={errors.beneficiaries}
              count={`${formData.beneficiaries.length}/300`}
            >
              <textarea
                name="beneficiaries"
                value={formData.beneficiaries}
                onChange={handleChange}
                maxLength={300}
                rows={4}
                placeholder="e.g. young people, local families, schools..."
              />
            </FormField>

            <FormField
              label="Expected community impact"
              count={`${formData.impact.length}/300`}
            >
              <textarea
                name="impact"
                value={formData.impact}
                onChange={handleChange}
                maxLength={300}
                rows={4}
                placeholder="e.g. improved wellbeing, more active lifestyles..."
              />
            </FormField>
          </section>

          <aside className="submission-side">
            <div className="upload-card">
              <h2>Attachments</h2>
              <p className="optional-text">Optional</p>

              <div className="upload-box">
                <div className="upload-icon">↑</div>
                <strong>Add supporting material</strong>
                <p>Upload images, PDFs, videos or audio files.</p>
                <small>PNG, JPG, JPEG, PDF, MP4, MOV, MP3, WAV, M4A · Max 10MB each</small>

                <label className="secondary-button upload-button">
                  Add files
                  <input
                    type="file"
                    accept=".png,.jpg,.jpeg,.pdf,.mp4,.mov,.mp3,.wav,.m4a"
                    multiple
                    hidden
                    onChange={handleAttachments}
                  />
                </label>

                {attachmentError && <p className="form-error">{attachmentError}</p>}

                {attachments.length > 0 && (
                  <div className="attachment-list">
                    {attachments.map((file, index) => (
                      <div className="attachment-item" key={`${file.name}-${index}`}>
                        <span>📎 {file.name}</span>
                        <button type="button" onClick={() => removeAttachment(index)}>Remove</button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div className="submission-actions-card">
              <label className="confirmation-row">
                <input
                  type="checkbox"
                  name="confirmation"
                  checked={formData.confirmation}
                  onChange={handleChange}
                />

                <span>
                  I confirm this submission is my own work and follows the
                  community guidelines.
                </span>
              </label>

              {errors.confirmation && (
                <p className="form-error">{errors.confirmation}</p>
              )}

              <button
                className="secondary-button full-width"
                type="button"
                onClick={handleDraft}
              >
                Save as Draft
              </button>

              <button
                className="black-button full-width"
                type="submit"
              >
                Submit Idea →
              </button>
            </div>

            <div className="help-card">
              <h3>Need help?</h3>
              <button type="button">Community Guidelines</button>
              <button type="button">Contact Support</button>
            </div>
          </aside>
        </form>
      </main>

      <ChatButton onNavigate={onNavigate} />
    </div>
  );
}

function FormField({
  label,
  required = false,
  helper,
  error,
  count,
  children,
}) {
  return (
    <div className="submission-field">
      <label>
        {label} {required && <span className="required">*</span>}
      </label>

      {helper && <p className="field-helper">{helper}</p>}

      {children}

      <div className="field-footer">
        <span className="form-error">{error || ""}</span>
        {count && <small>{count}</small>}
      </div>
    </div>
  );
}