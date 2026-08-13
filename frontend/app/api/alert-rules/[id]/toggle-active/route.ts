import { ALERT_RULE_SERVICE_URL, proxyFetch } from "@/lib/backend";

export async function PATCH(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  return proxyFetch(`${ALERT_RULE_SERVICE_URL}/api/alert-rules/${id}/toggle-active`, {
    method: "PATCH",
  });
}
