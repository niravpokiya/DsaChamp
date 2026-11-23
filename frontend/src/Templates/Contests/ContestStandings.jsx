import { useEffect, useState } from "react";
import { getStandings } from "./contestApi";
import { formatContestTime } from "./contestDisplay";

export default function ContestStandings({ contestId }) {
  const [page, setPage] = useState(0);
  const [snapshot, setSnapshot] = useState(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const request = new AbortController();
    let timer;
    async function refresh() {
      try {
        const standings = await getStandings(contestId, page, request.signal);
        if (request.signal.aborted) return;
        setSnapshot({ page, standings });
        if (!standings.provisional) return;
      } catch {
        if (request.signal.aborted) return;
        setSnapshot((previous) => ({ page, standings: previous?.page === page ? previous.standings : null, failed: true }));
      }
      timer = setTimeout(refresh, 5000);
    }
    refresh();
    return () => {
      request.abort();
      clearTimeout(timer);
    };
  }, [contestId, page, retry]);

  const current = snapshot?.page === page ? snapshot : null;
  const standings = current?.standings;
  return (
    <section className="contest-card contest-standings" aria-labelledby="contest-standings-title">
      <h2 id="contest-standings-title">Standings</h2>
      {standings?.provisional && <p>Provisional · standings update while judging is in progress.</p>}
      {!current && <p role="status">Loading standings…</p>}
      {current?.failed && <p role="alert">Could not refresh standings. <button type="button" onClick={() => setRetry(retry + 1)}>Retry</button></p>}
      {standings?.rows.length === 0 && <p>No accepted submissions on this page.</p>}
      {standings?.rows.length > 0 && (
        <div className="contest-table-scroll">
          <table>
            <thead><tr><th scope="col">Rank</th><th scope="col">Player</th><th scope="col">Solved</th><th scope="col">Points</th><th scope="col">Score reached</th></tr></thead>
            <tbody>{standings.rows.map(({ score, rank }) => (
              <tr key={score.userId}><td>{rank}</td><td>Player #{score.userId}</td><td>{score.solved}</td><td>{score.points}</td><td>{formatContestTime(score.scoreReachedAt)}</td></tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {(page > 0 || standings?.hasNext) && <nav className="contest-actions" aria-label="Standings pages">
        <button type="button" disabled={!page} onClick={() => setPage(page - 1)}>Previous</button>
        <span>Page {page + 1}</span>
        <button type="button" disabled={!standings?.hasNext} onClick={() => setPage(page + 1)}>Next</button>
      </nav>}
    </section>
  );
}
