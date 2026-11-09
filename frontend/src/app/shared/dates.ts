/** `yyyy-MM-dd` in the user's time zone, the format of the API's LocalDate fields. */
export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** `yyyy-MM-dd` of the day `days` days before today. */
export function daysAgo(days: number, today: Date = new Date()): string {
  const date = new Date(today);
  date.setDate(date.getDate() - days);
  return toIsoDate(date);
}
