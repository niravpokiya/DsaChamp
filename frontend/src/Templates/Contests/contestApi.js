import { api } from "../../utils/api";

const contestPath = (id) => `/contests/${encodeURIComponent(id)}`;
const data = (request) => request.then((response) => response.data);

export const listContests = (page, signal) => data(api.get("/contests", { params: { page, size: 21 }, signal }));
export const getContest = (id, signal) => data(api.get(contestPath(id), { signal }));
export const getRegistration = (id, signal) => data(api.get(`${contestPath(id)}/registration`, { signal }));
export const joinContest = (id, signal) => api.put(`${contestPath(id)}/registration`, null, { signal });
export const scheduleContest = (request) => data(api.post("/contests", request));
export const getStandings = (id, page, signal) =>
  data(api.get(`${contestPath(id)}/standings`, { params: { page, size: 20 }, signal }));
export const submitContestSolution = (id, request) => data(api.post(`${contestPath(id)}/submissions`, request));
export const getContestVerdict = (jobId, signal) => data(api.get(`/code/submit/${encodeURIComponent(jobId)}`, { signal }));

export function contestError(error, fallback) {
  return error.response?.data?.detail || error.response?.data?.message || fallback;
}
