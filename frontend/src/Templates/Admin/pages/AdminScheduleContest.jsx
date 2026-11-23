import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../../utils/api";
import { contestError, scheduleContest } from "../../Contests/contestApi";
import "../../Contests/contests.css";

function problemSlug(title) {
  return title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
}

export default function AdminScheduleContest() {
  const [catalog, setCatalog] = useState(null);
  const [catalogError, setCatalogError] = useState(false);
  const [catalogRetry, setCatalogRetry] = useState(0);
  const [title, setTitle] = useState("");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [selection, setSelection] = useState([]);
  const [candidate, setCandidate] = useState("");
  const [review, setReview] = useState(null);
  const [scheduled, setScheduled] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const request = new AbortController();
    api.get("/question/all-published", { signal: request.signal })
      .then(({ data }) => setCatalog(data.data ?? []))
      .catch(() => {
        if (!request.signal.aborted) setCatalogError(true);
      });
    return () => request.abort();
  }, [catalogRetry]);

  function addProblem() {
    const problem = catalog.find(({ id }) => id === candidate);
    if (!problem || selection.length >= 20 || selection.some(({ id }) => id === problem.id)) return;
    setSelection([...selection, { id: problem.id, title: problem.title, slug: problemSlug(problem.title), points: "100" }]);
    setCandidate("");
  }

  function reviewSchedule(event) {
    event.preventDefault();
    setError("");
    const startsAt = new Date(`${start}Z`);
    const endsAt = new Date(`${end}Z`);
    if (!title.trim() || !(startsAt.getTime() > Date.now()) || !(endsAt > startsAt)) {
      setError("Enter a title, a future UTC start, and an end after the start.");
      return;
    }
    if (!selection.length || selection.some(({ slug, points }) => !slug
      || !Number.isInteger(Number(points)) || Number(points) < 1 || Number(points) > 1000)) {
      setError("Choose at least one problem and enter whole-number points from 1 to 1,000.");
      return;
    }
    setReview({ title: title.trim(), startsAt: startsAt.toISOString(), endsAt: endsAt.toISOString(),
      problems: selection.map(({ slug, points }) => ({ slug, points: Number(points) })) });
  }

  async function confirmSchedule() {
    setSaving(true);
    setError("");
    try {
      setScheduled(await scheduleContest(review));
    } catch (failure) {
      setError(contestError(failure, "Could not confirm scheduling. Check the contest list before retrying."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="contest-shell contest-admin">
      <header className="contest-hero"><h1>Schedule a contest</h1><p>Set the window, select published problems, and review the scoring before scheduling.</p></header>
      {scheduled ? (
        <section className="contest-card contest-admin__fields">
          <h2>Contest scheduled</h2>
          <p>{scheduled.title}</p>
          <Link to={`/contests/${scheduled.id}`}>Open contest lobby</Link>
        </section>
      ) : review ? (
        <section className="contest-card contest-admin__fields">
          <h2>Review contest</h2>
          <strong>{review.title}</strong>
          <p>Starts {review.startsAt.replace("T", " ").slice(0, 16)} UTC</p>
          <p>Ends {review.endsAt.replace("T", " ").slice(0, 16)} UTC</p>
          <ol className="contest-problems">{selection.map((problem) => (
            <li key={problem.id}><strong>{problem.title}</strong><span>{problem.points} points</span></li>
          ))}</ol>
          <div className="contest-actions">
            <button type="button" disabled={saving} onClick={() => { setReview(null); setError(""); }}>Edit</button>
            <button type="button" disabled={saving} onClick={confirmSchedule}>{saving ? "Scheduling…" : "Schedule contest"}</button>
          </div>
        </section>
      ) : (
        <form className="contest-card contest-admin__fields" onSubmit={reviewSchedule}>
          <label>Title <input required maxLength={120} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <div className="contest-grid">
            <label>Starts at (UTC) <input type="datetime-local" required value={start} onChange={(event) => setStart(event.target.value)} /></label>
            <label>Ends at (UTC) <input type="datetime-local" required value={end} onChange={(event) => setEnd(event.target.value)} /></label>
          </div>
          <h2>Problems</h2>
          {catalogError && <p role="alert">Could not load published problems. <button type="button"
            onClick={() => { setCatalogError(false); setCatalogRetry(catalogRetry + 1); }}>Retry</button></p>}
          {catalog?.length === 0 && <p>Publish a problem before scheduling a contest.</p>}
          <div className="contest-actions">
            <label>Published problem <select value={candidate} disabled={!catalog || selection.length >= 20}
              onChange={(event) => setCandidate(event.target.value)}>
              <option value="">{catalog ? "Choose a problem" : "Loading problems…"}</option>
              {catalog?.filter((problem) => !selection.some(({ id }) => id === problem.id)).map((problem) => (
                <option key={problem.id} value={problem.id}>{problem.title} · {problem.difficulty}</option>
              ))}
            </select></label>
            <button type="button" disabled={!candidate} onClick={addProblem}>Add problem</button>
          </div>
          <ol className="contest-problems">{selection.map((problem) => (
            <li key={problem.id}>
              <strong>{problem.title}</strong>
              <div className="contest-actions">
                <label>Points <input aria-label={`Points for ${problem.title}`} required min={1} max={1000} type="number" value={problem.points}
                  onChange={(event) => setSelection(selection.map((entry) => entry.id === problem.id
                    ? { ...entry, points: event.target.value } : entry))} /></label>
                <button type="button" aria-label={`Remove ${problem.title}`}
                  onClick={() => setSelection(selection.filter(({ id }) => id !== problem.id))}>Remove</button>
              </div>
            </li>
          ))}</ol>
          <button type="submit" disabled={!selection.length}>Review contest</button>
        </form>
      )}
      {error && <p role="alert">{error}</p>}
    </main>
  );
}
