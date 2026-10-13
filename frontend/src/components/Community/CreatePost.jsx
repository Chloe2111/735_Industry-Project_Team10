import { useState } from "react";
import "./CommunityWorkspace.css";
import CommunityHeader from "./CommunityHeader";
import { useToast } from "../Toast/ToastProvider";

const allowedExtensions = ["png", "jpg", "jpeg", "pdf", "mp4", "mov", "mp3", "wav", "m4a"];
const MAX_FILE_SIZE = 10 * 1024 * 1024;

export default function CreatePost({ onNavigate, groups = [], selectedGroup, onCreatePost }) {
  const [groupId, setGroupId] = useState(selectedGroup?.id || groups[0]?.id || "");
  const [title, setTitle] = useState("");
  const [text, setText] = useState("");
  const [files, setFiles] = useState([]);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const toast = useToast();

  function handleFiles(event) {
    const selectedFiles = Array.from(event.target.files);
    const invalidFile = selectedFiles.find((file) => {
      const extension = file.name.split(".").pop()?.toLowerCase();
      return !allowedExtensions.includes(extension) || file.size > MAX_FILE_SIZE;
    });
    if (invalidFile) { setError("Files must be PNG, JPG, JPEG, PDF, MP4, MOV, MP3, WAV or M4A and no larger than 10MB."); return; }
    setError(""); setFiles((previous) => [...previous, ...selectedFiles]);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (!groupId) { setError("Please select a group for this post."); return; }
    if (!title.trim()) { setError("Please add a post title."); return; }
    if (!text.trim()) { setError("Please write something before creating your post."); return; }
    // Story 17 stores text posts. Attachment selection/validation is UI-only until an upload endpoint is agreed.
    setSubmitting(true); setError("");
    try {
      await onCreatePost({ groupId, title, content: text });
      onNavigate(selectedGroup?.id === groupId ? "groupView" : "dashboard", selectedGroup?.id === groupId ? selectedGroup : undefined);
    } catch (err) {
      const message = err.message || "Unable to create the post. Please try again.";
      setError(message); toast.error(message);
    } finally { setSubmitting(false); }
  }

  return <div className="community-page"><CommunityHeader active="dashboard" onNavigate={onNavigate} /><div className="breadcrumb">Community Portal › Create Post</div><main className="form-page"><button className="back-button" onClick={() => onNavigate("dashboard")}>← Back</button><div className="page-heading"><h1>Create Post</h1><p>Share an update, idea or resource with your community.</p></div><form className="community-card large-form" onSubmit={handleSubmit}>
    <label>Group *<select value={groupId} onChange={(e) => setGroupId(e.target.value)}><option value="">Select a group</option>{groups.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}</select></label>
    <label>Title *<input value={title} onChange={(e) => setTitle(e.target.value)} maxLength="120" placeholder="Post title" /></label>
    <label>Post *<textarea value={text} onChange={(e) => setText(e.target.value)} maxLength="1000" placeholder="What would you like to share?" /><small>{text.length}/1000</small></label>
    <div className="attachment-area"><h3>Attachments</h3><p>Add images, PDFs, videos or audio files.</p><label className="upload-button">+ Add files<input type="file" accept=".png,.jpg,.jpeg,.pdf,.mp4,.mov,.mp3,.wav,.m4a" multiple hidden onChange={handleFiles} /></label><small>Maximum 10MB per file. Upload storage is not part of the current Story 17 API.</small></div>
    {files.length > 0 && <div className="selected-files">{files.map((file, index) => <div className="selected-file" key={`${file.name}-${index}`}><span>📎 {file.name}</span><button type="button" onClick={() => setFiles((previous) => previous.filter((_, i) => i !== index))}>Remove</button></div>)}</div>}
    {error && <div className="form-error">{error}</div>}<div className="form-buttons"><button type="button" className="secondary-button" onClick={() => onNavigate("dashboard")} disabled={submitting}>Cancel</button><button type="submit" className="primary-button" disabled={submitting}>{submitting ? "Posting..." : "Post"}</button></div>
  </form></main></div>;
}
