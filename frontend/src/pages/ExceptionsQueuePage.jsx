export function ExceptionsQueuePage() {
  return (
    <div className="exceptions-queue-page">
      <h1>Exceptions queue</h1>
      <p className="exceptions-queue-page__subtitle">
        AI review flags waiting on a human decision before a report can be released.
      </p>

      <div className="page-card page-card--wide">
        <p>
          Reserved for the Story 19 exceptions queue (Clear / Reject flagged content). This page will host the real
          component once that work is merged in — swap this placeholder for <code>ExceptionsQueue</code> at that
          point without touching routing or navigation elsewhere.
        </p>
      </div>
    </div>
  )
}
