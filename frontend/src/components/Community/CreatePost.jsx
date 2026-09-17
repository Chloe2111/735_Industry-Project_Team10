import { useState } from "react";
import "./CommunityWorkspace.css";
import CommunityHeader from "./CommunityHeader";

const allowedExtensions = [
  "png",
  "jpg",
  "jpeg",
  "pdf",
  "mp4",
  "mov",
  "mp3",
  "wav",
  "m4a",
];

const MAX_FILE_SIZE = 10 * 1024 * 1024;

export default function CreatePost({ onNavigate }) {
  const [text, setText] = useState("");
  const [files, setFiles] = useState([]);
  const [error, setError] = useState("");

  function handleFiles(event) {
    const selectedFiles = Array.from(event.target.files);

    const invalidFile = selectedFiles.find((file) => {
      const extension = file.name.split(".").pop()?.toLowerCase();

      return (
        !allowedExtensions.includes(extension) ||
        file.size > MAX_FILE_SIZE
      );
    });

    if (invalidFile) {
      setError(
        "Files must be PNG, JPG, JPEG, PDF, MP4, MOV, MP3, WAV or M4A and no larger than 10MB."
      );
      return;
    }

    setError("");
    setFiles((previous) => [...previous, ...selectedFiles]);
  }

  function removeFile(index) {
    setFiles((previous) =>
      previous.filter((_, fileIndex) => fileIndex !== index)
    );
  }

  function handleSubmit(event) {
    event.preventDefault();

    if (!text.trim()) {
      setError("Please write something before creating your post.");
      return;
    }

    setError("");
    onNavigate("dashboard");
  }

  return (
    <div className="community-page">
      <CommunityHeader active="dashboard" onNavigate={onNavigate} />

      <div className="breadcrumb">
        Community Portal › Create Post
      </div>

      <main className="form-page">
        <button
          className="back-button"
          onClick={() => onNavigate("dashboard")}
        >
          ← Back
        </button>

        <div className="page-heading">
          <h1>Create Post</h1>
          <p>Share an update, idea or resource with your community.</p>
        </div>

        <form className="community-card large-form" onSubmit={handleSubmit}>
          <label>
            Post *
            <textarea
              value={text}
              onChange={(event) => setText(event.target.value)}
              maxLength="1000"
              placeholder="What would you like to share?"
            />
            <small>{text.length}/1000</small>
          </label>

          <div className="attachment-area">
            <h3>Attachments</h3>

            <p>
              Add images, PDFs, videos or audio files.
            </p>

            <label className="upload-button">
              + Add files
              <input
                type="file"
                accept=".png,.jpg,.jpeg,.pdf,.mp4,.mov,.mp3,.wav,.m4a"
                multiple
                hidden
                onChange={handleFiles}
              />
            </label>

            <small>Maximum 10MB per file</small>
          </div>

          {files.length > 0 && (
            <div className="selected-files">
              {files.map((file, index) => (
                <div className="selected-file" key={`${file.name}-${index}`}>
                  <span>📎 {file.name}</span>

                  <button
                    type="button"
                    onClick={() => removeFile(index)}
                  >
                    Remove
                  </button>
                </div>
              ))}
            </div>
          )}

          {error && <div className="form-error">{error}</div>}

          <div className="form-buttons">
            <button
              type="button"
              className="secondary-button"
              onClick={() => onNavigate("dashboard")}
            >
              Cancel
            </button>

            <button type="submit" className="primary-button">
              Post
            </button>
          </div>
        </form>
      </main>
    </div>
  );
}