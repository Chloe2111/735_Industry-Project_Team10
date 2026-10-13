import { useState } from "react";
import "./CommunityWorkspace.css";
import CommunityHeader, { Avatar } from "./CommunityHeader";

export default function Messages({ onNavigate }) {
  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([
    {
      id: 1,
      sender: "Jordan",
      text: "Hi Alex! Are you joining the community workshop?",
      mine: false,
    },
    {
      id: 2,
      sender: "Alex",
      text: "Yes, I'm planning to attend.",
      mine: true,
    },
  ]);

  function sendMessage(event) {
    event.preventDefault();

    if (!message.trim()) return;

    setMessages((previous) => [
      ...previous,
      {
        id: Date.now(),
        sender: "Alex",
        text: message.trim(),
        mine: true,
      },
    ]);

    setMessage("");
  }

  return (
    <div className="community-page">
      <CommunityHeader active="messages" onNavigate={onNavigate} />

      <div className="breadcrumb">Community Portal › Messages</div>

      <main className="messages-page">
        <aside className="conversation-list">
          <div className="messages-heading">
            <h1>Messages</h1>
            <button>+</button>
          </div>

          <input type="search" placeholder="Search conversations..." />

          <button className="conversation active">
            <Avatar initials="JM" />

            <div>
              <strong>Jordan Mitchell</strong>
              <span>Are you joining the workshop?</span>
            </div>

            <b>2</b>
          </button>

          <button className="conversation">
            <Avatar initials="SC" />

            <div>
              <strong>Sophie Chen</strong>
              <span>Thanks for your feedback!</span>
            </div>
          </button>
        </aside>

        <section className="chat-panel">
          <header className="chat-header">
            <Avatar initials="JM" />

            <div>
              <strong>Jordan Mitchell</strong>
              <span>Local Community Group</span>
            </div>
          </header>

          <div className="chat-messages">
            {messages.map((item) => (
              <div
                key={item.id}
                className={
                  item.mine ? "message-bubble mine" : "message-bubble"
                }
              >
                {item.text}
              </div>
            ))}
          </div>

          <form className="message-composer" onSubmit={sendMessage}>
            <button type="button">📎</button>

            <input
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              placeholder="Write a message..."
            />

            <button type="submit" className="primary-button">
              Send
            </button>
          </form>
        </section>
      </main>
    </div>
  );
}