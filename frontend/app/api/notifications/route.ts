import { NextRequest } from "next/server";
import { NOTIFICATION_SERVICE_URL, proxyFetch } from "@/lib/backend";

export async function GET(request: NextRequest) {
  const userId = request.nextUrl.searchParams.get("userId");
  return proxyFetch(`${NOTIFICATION_SERVICE_URL}/api/notifications?userId=${userId}`);
}
