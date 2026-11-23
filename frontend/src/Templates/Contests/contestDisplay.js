const dateFormat = new Intl.DateTimeFormat(undefined, {
  dateStyle: "medium",
  timeStyle: "short",
});

export const formatContestTime = (value) => dateFormat.format(new Date(value));
export const formatProblemSlug = (slug) => slug.split("-").map((word) => word[0].toUpperCase() + word.slice(1)).join(" ");
export const phaseLabel = { SCHEDULED: "Upcoming", RUNNING: "Live", ENDED: "Ended" };

export function countdownTo(value, now) {
  const seconds = Math.max(0, Math.ceil((new Date(value).getTime() - now) / 1000));
  const hours = Math.floor(seconds / 3600);
  return [hours, Math.floor(seconds % 3600 / 60), seconds % 60]
    .map((part) => String(part).padStart(2, "0")).join(":");
}
