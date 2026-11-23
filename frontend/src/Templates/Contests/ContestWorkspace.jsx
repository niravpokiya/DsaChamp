import Editor from "@monaco-editor/react";
import MDEditor from "@uiw/react-md-editor";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useTheme } from "../../Context/themeContext";
import languageSnippets from "../../snippets/snippet";
import SubmissionResultTab from "../submission-result-tab";
import { api } from "../../utils/api";
import { contestError, getContest, getContestVerdict, getRegistration, submitContestSolution } from "./contestApi";
import { countdownTo } from "./contestDisplay";
import "./contests.css";

const PENDING = new Set(["PENDING", "QUEUED", "RUNNING"]);

function useWorkspace(contestId, questionId) {
  const [screen, setScreen] = useState(null);

  useEffect(() => {
    const request = new AbortController();
    async function load() {
      try {
        const [contest, registration] = await Promise.all([
          getContest(contestId, request.signal),
          getRegistration(contestId, request.signal),
        ]);
        const selected = contest.problems.find((problem) => problem.questionId === questionId);
        if (!selected || !registration.joined) {
          const reason = contest.phase === "SCHEDULED" ? "Problems unlock when the contest starts."
            : !registration.joined ? "Join the contest from the lobby first." : "This problem isn’t in the contest.";
          setScreen({ error: reason });
          return;
        }
        const { data } = await api.get(`/question/id/${encodeURIComponent(questionId)}`, { signal: request.signal });
        setScreen({ contest, problem: data.data, selected });
      } catch {
        if (!request.signal.aborted) setScreen({ error: "Could not load this contest problem." });
      }
    }
    load();
    return () => request.abort();
  }, [contestId, questionId]);
  return screen;
}

function Workspace({ contestId, questionId }) {
  const screen = useWorkspace(contestId, questionId);
  const { currentMode } = useTheme();
  const [language, setLanguage] = useState("cpp");
  const [drafts, setDrafts] = useState({});
  const [now, setNow] = useState(Date.now);
  const [attempt, setAttempt] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [pollFailed, setPollFailed] = useState(false);
  const [pollRetry, setPollRetry] = useState(0);
  const code = drafts[language] ?? languageSnippets[language].code;
  const jobId = attempt?.jobId;

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!jobId) return;
    const request = new AbortController();
    let timer;
    let failures = 0;
    async function poll() {
      try {
        const result = await getContestVerdict(jobId, request.signal);
        if (request.signal.aborted) return;
        failures = 0;
        setAttempt({ jobId, result });
        if (!PENDING.has(result.status)) return;
      } catch {
        if (request.signal.aborted) return;
        if (++failures === 3) {
          setPollFailed(true);
          return;
        }
      }
      timer = setTimeout(poll, 1500);
    }
    poll();
    return () => {
      request.abort();
      clearTimeout(timer);
    };
  }, [jobId, pollRetry]);

  async function submit() {
    setError("");
    setPollFailed(false);
    setSubmitting(true);
    try {
      const receipt = await submitContestSolution(contestId, { questionId, language, sourceCode: code });
      setAttempt({ jobId: receipt.jobId, result: { status: "QUEUED" } });
    } catch (failure) {
      setError(contestError(failure, "Could not confirm submission. Check your submissions before trying again."));
    } finally {
      setSubmitting(false);
    }
  }

  const pending = PENDING.has(attempt?.result.status);
  const open = screen?.contest?.phase === "RUNNING" && now < Date.parse(screen.contest.endsAt);

  return (
    <main className="contest-shell">
      <Link className="contest-back" to={`/contests/${contestId}`}>← Back to lobby</Link>
      {!screen && <p role="status">Loading problem…</p>}
      {screen?.error && <p role="alert">{screen.error}</p>}
      {screen?.problem && (
        <>
          <header className="contest-hero">
            <p>{screen.contest.title} · {screen.selected.points} points</p>
            <h1>{screen.problem.title}</h1>
            <p>{open ? `Time remaining · ${countdownTo(screen.contest.endsAt, now)}` : "Contest submissions are closed."}</p>
          </header>
          <div className="contest-workspace">
            <section className="contest-card contest-statement" aria-label="Problem statement" data-color-mode={currentMode}>
              <MDEditor.Markdown source={screen.problem.description ?? ""} />
              {screen.problem.examples?.map((example, index) => (
                <details key={index}>
                  <summary>Example {index + 1}</summary>
                  <p>Input</p><pre>{example.input}</pre>
                  <p>Expected output</p><pre>{example.output}</pre>
                  {example.explanation && <p>{example.explanation}</p>}
                </details>
              ))}
            </section>
            <section className="contest-card contest-code">
              <label>Language <select value={language} onChange={(event) => setLanguage(event.target.value)}>
                <option value="cpp">C++</option><option value="java">Java</option><option value="python">Python</option>
              </select></label>
              <Editor height="400px" language={languageSnippets[language].monacoLanguage} value={code}
                theme={currentMode === "dark" ? "vs-dark" : "light"}
                options={{ ariaLabel: "Contest solution", minimap: { enabled: false }, scrollBeyondLastLine: false }}
                onChange={(value) => setDrafts((previous) => ({ ...previous, [language]: value ?? "" }))} />
              <div className="contest-actions">
                <button type="button" disabled={!open || submitting || pending || !code.trim() || code.length > 65536}
                  onClick={submit}>{submitting ? "Submitting…" : "Submit to contest"}</button>
                {jobId && <Link to={`/submissions/${jobId}`}>Submission details</Link>}
              </div>
              {code.length > 65536 && <p role="alert">Code exceeds the 65,536-character limit.</p>}
              {error && <p role="alert">{error}</p>}
            </section>
          </div>
          {pollFailed && <p role="alert">Verdict updates are temporarily unavailable. <button type="button" onClick={() => {
            setPollFailed(false);
            setPollRetry((previous) => previous + 1);
          }}>Retry status</button></p>}
          {(submitting || attempt) && <SubmissionResultTab submissionResult={attempt?.result} submitting={submitting || pending} />}
        </>
      )}
    </main>
  );
}

export default function ContestWorkspace() {
  const { contestId, questionId } = useParams();
  return <Workspace key={`${contestId}:${questionId}`} contestId={contestId} questionId={questionId} />;
}
