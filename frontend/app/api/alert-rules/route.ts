import { NextRequest } from "next/server";
import { ALERT_RULE_SERVICE_URL, proxyFetch } from "@/lib/backend";

export async function GET(request: NextRequest) {
  const userId = request.nextUrl.searchParams.get("userId");
  return proxyFetch(`${ALERT_RULE_SERVICE_URL}/api/alert-rules?userId=${userId}`);
}

export async function POST(request: NextRequest) {
  const body = await request.text();
  return proxyFetch(`${ALERT_RULE_SERVICE_URL}/api/alert-rules`, {
    method: "POST",
    body,
  });
}
