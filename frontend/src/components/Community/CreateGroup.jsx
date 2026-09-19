import { useState } from "react";
import "./CommunityWorkspace.css";
import CommunityHeader from "./CommunityHeader";
import { useToast } from "../Toast/ToastProvider";

export default function CreateGroup({ onNavigate, onCreateGroup }) {
  const [form, setForm] = useState({ name: "", description: "", category: "", privacy: "public" });
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const toast = useToast();

  function handleChange(event) {
    const { name, value } = event.target;
    setForm((previous) => ({ ...previous, [name]: value }));
    setErrors((previous) => ({ ...previous, [name]: undefined, submit: undefined }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const newErrors = {};
    if (!form.name.trim()) newErrors.name = "Group name is required.";
    else if (form.name.trim().length < 3) newErrors.name = "Group name must contain at least 3 characters.";
    if (!form.description.trim()) newErrors.description = "Please provide a group description.";
    if (!form.category) newErrors.category = "Please select a category.";
    setErrors(newErrors);
    if (Object.keys(newErrors).length) return;

    setSubmitting(true);
    try {
      await onCreateGroup(form);
      onNavigate("groups");
    } catch (error) {
      const message = error.message || "Unable to create the group. Please try again.";
      setErrors({ submit: message });
      toast.error(message);
    } finally {
      setSubmitting(false);
    }
  }

  return <div className="community-page">
    <CommunityHeader active="groups" onNavigate={onNavigate} />
    <div className="breadcrumb">Community Portal › Groups › Create Group</div>
    <main className="form-page">
      <button className="back-button" onClick={() => onNavigate("groups")}>← Back to Groups</button>
      <div className="page-heading"><h1>Create a Group</h1><p>Create a community space where members can connect and collaborate.</p></div>
      <form className="community-card large-form" onSubmit={handleSubmit}>
        <label>Group name *<input name="name" value={form.name} onChange={handleChange} maxLength="100" placeholder="Enter a group name" />{errors.name && <span className="form-error">{errors.name}</span>}</label>
        <label>Description *<textarea name="description" value={form.description} onChange={handleChange} maxLength="500" placeholder="What is this group about?" /><small>{form.description.length}/500</small>{errors.description && <span className="form-error">{errors.description}</span>}</label>
        <label>Category *<select name="category" value={form.category} onChange={handleChange}><option value="">Select a category</option><option>Community</option><option>Design</option><option>Sustainability</option><option>Youth</option><option>Infrastructure</option></select>{errors.category && <span className="form-error">{errors.category}</span>}</label>
        <fieldset><legend>Group privacy</legend><label className="radio-row"><input type="radio" name="privacy" value="public" checked={form.privacy === "public"} onChange={handleChange} />Public — anyone can view and join</label><label className="radio-row"><input type="radio" name="privacy" value="private" checked={form.privacy === "private"} onChange={handleChange} />Private — members must be approved</label></fieldset>
        {errors.submit && <div className="form-error">{errors.submit}</div>}
        <div className="form-buttons"><button type="button" className="secondary-button" onClick={() => onNavigate("groups")} disabled={submitting}>Cancel</button><button type="submit" className="primary-button" disabled={submitting}>{submitting ? "Creating..." : "Create Group"}</button></div>
      </form>
    </main>
  </div>;
}
