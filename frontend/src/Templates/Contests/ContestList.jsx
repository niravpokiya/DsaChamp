import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Trophy } from "lucide-react";
import { listContests } from "./contestApi";
import { formatContestTime, phaseLabel } from "./contestDisplay";
import "./contests.css";

const PAGE_SIZE = 21;

export default function ContestList() {
  const [page, setPage] = useState(0);
  const [retry, setRetry] = useState(0);
  const [result, setResult] = useState(null);

  useEffect(() => {
    const request = new AbortController();
    listContests(page, request.signal)
      .then((contests) => setResult({ page, retry, contests }))
      .catch(() => {
        if (!request.signal.aborted) setResult({ page, retry, failed: true });
      });
    return () => request.abort();
  }, [page, retry]);

  const current = result?.page === page && result?.retry === retry ? result : null;
  return (
    <main className="contest-shell">
      <header className="contest-hero">
        <Trophy size={27} aria-hidden="true" />
        <h1>Contests</h1>
        <p>Join a scheduled round, solve problems, and see where you rank.</p>
      </header>
      {!current && <p role="status">Loading contests…</p>}
      {current?.failed && <p role="alert">Could not load contests. <button type="button" onClick={() => setRetry(retry + 1)}>Retry</button></p>}
      {current?.contests?.length === 0 && <p>No contests on this page.</p>}
      {current?.contests?.length > 0 && (
        <div className="contest-grid">
          {current.contests.map((contest) => (
            <Link className="contest-card contest-card--link" key={contest.id} to={`/contests/${contest.id}`}>
              <span className="contest-phase" data-phase={contest.phase}>{phaseLabel[contest.phase]}</span>
              <h2>{contest.title}</h2>
              <p>{formatContestTime(contest.startsAt)}</p>
              <span className="contest-card__action">Open lobby <ArrowRight size={16} aria-hidden="true" /></span>
            </Link>
          ))}
        </div>
      )}
      {(page > 0 || current?.contests?.length === PAGE_SIZE) && (
        <nav className="contest-actions" aria-label="Contest pages">
          <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span>Page {page + 1}</span>
          <button type="button" disabled={current?.contests?.length !== PAGE_SIZE} onClick={() => setPage(page + 1)}>Next</button>
        </nav>
      )}
    </main>
  );
}
