import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { CalendarDays } from "lucide-react";
import { contestError, getContest, getRegistration, joinContest } from "./contestApi";
import { countdownTo, formatContestTime, formatProblemSlug, phaseLabel } from "./contestDisplay";
import ContestStandings from "./ContestStandings";
import "./contests.css";

function Lobby({ contestId }) {
  const [snapshot, setSnapshot] = useState(null);
  const [joined, setJoined] = useState(null);
  const [now, setNow] = useState(Date.now);
  const [retry, setRetry] = useState(0);
  const [joinError, setJoinError] = useState("");
  const [joining, setJoining] = useState(false);

  useEffect(() => {
    const request = new AbortController();
    let timer;
    async function refresh() {
      try {
        const contest = await getContest(contestId, request.signal);
        if (!request.signal.aborted) setSnapshot({ contest });
      } catch {
        if (!request.signal.aborted) setSnapshot((previous) => previous?.contest ? previous : { failed: true });
      }
      if (!request.signal.aborted) timer = setTimeout(refresh, 5000);
    }
    refresh();
    return () => {
      request.abort();
      clearTimeout(timer);
    };
  }, [contestId, retry]);

  useEffect(() => {
    const request = new AbortController();
    getRegistration(contestId, request.signal)
      .then((registration) => setJoined(registration.joined))
      .catch(() => {
        if (!request.signal.aborted) setJoinError("Could not check registration.");
      });
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      request.abort();
      clearInterval(interval);
    };
  }, [contestId, retry]);

  async function join() {
    setJoining(true);
    setJoinError("");
    try {
      await joinContest(contestId);
      setJoined(true);
    } catch (error) {
      setJoinError(contestError(error, "Could not join the contest."));
    } finally {
      setJoining(false);
    }
  }

  const contest = snapshot?.contest;
  return (
    <main className="contest-shell">
      <Link className="contest-back" to="/contests">← All contests</Link>
      {!snapshot && <p role="status">Loading contest…</p>}
      {snapshot?.failed && <p role="alert">Could not load this contest. <button type="button" onClick={() => setRetry(retry + 1)}>Retry</button></p>}
      {contest && (
        <>
          <header className="contest-hero">
            <span className="contest-phase" data-phase={contest.phase}>{phaseLabel[contest.phase]}</span>
            <h1>{contest.title}</h1>
            <p><CalendarDays size={16} aria-hidden="true" /> {formatContestTime(contest.startsAt)} – {formatContestTime(contest.endsAt)}</p>
            {contest.phase !== "ENDED" && (
              <div className="contest-countdown">
                <span>{contest.phase === "RUNNING" ? "Time remaining" : "Starts in"}</span>
                <strong>{countdownTo(contest.phase === "RUNNING" ? contest.endsAt : contest.startsAt, now)}</strong>
              </div>
            )}
            <div className="contest-actions">
              {joined && <strong>Registered</strong>}
              {joined === false && contest.phase !== "ENDED" && (
                <button type="button" disabled={joining} onClick={join}>{joining ? "Joining…" : "Join contest"}</button>
              )}
              {joinError && <span role="alert">{joinError} {joined === null && <button type="button" onClick={() => { setJoinError(""); setRetry(retry + 1); }}>Retry</button>}</span>}
            </div>
          </header>
          <section className="contest-card">
            <h2>Problems</h2>
            {contest.phase === "SCHEDULED" ? <p>Problems unlock when the contest starts.</p> : (
              <ul className="contest-problems">
                {contest.problems.map((problem) => (
                  <li key={problem.questionId}>
                    {joined ? (
                      <Link to={`/contests/${contestId}/problems/${problem.questionId}`}>{formatProblemSlug(problem.slug)}</Link>
                    ) : <strong>{formatProblemSlug(problem.slug)}</strong>}
                    <span>{problem.difficulty} · {problem.points} points</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
          {contest.phase !== "SCHEDULED" && <ContestStandings contestId={contestId} />}
        </>
      )}
    </main>
  );
}

export default function ContestLobby() {
  const { contestId } = useParams();
  return <Lobby key={contestId} contestId={contestId} />;
}
