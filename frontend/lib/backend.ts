export const ALERT_RULE_SERVICE_URL =
  process.env.ALERT_RULE_SERVICE_URL ?? "http://localhost:8081";

export const NOTIFICATION_SERVICE_URL =
  process.env.NOTIFICATION_SERVICE_URL ?? "http://localhost:8082";

export async function proxyFetch(url: string, init?: RequestInit) {
  const upstream = await fetch(url, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
    cache: "no-store",
  });

  const body = await upstream.text();
  return new Response(body === "" ? null : body, {
    status: upstream.status,
    headers: upstream.headers.get("Content-Type")
      ? { "Content-Type": upstream.headers.get("Content-Type")! }
      : undefined,
  });
}
