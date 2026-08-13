import Dashboard from "./dashboard";
import { ALERT_RULE_SERVICE_URL, NOTIFICATION_SERVICE_URL } from "@/lib/backend";
import type { AlertRule, Notification } from "@/lib/types";

const USER_ID = 1;

export default async function Home() {
  const [rules, notifications] = await Promise.all([
    fetch(`${ALERT_RULE_SERVICE_URL}/api/alert-rules?userId=${USER_ID}`, {
      cache: "no-store",
    }).then((res) => res.json() as Promise<AlertRule[]>),
    fetch(`${NOTIFICATION_SERVICE_URL}/api/notifications?userId=${USER_ID}`, {
      cache: "no-store",
    }).then((res) => res.json() as Promise<Notification[]>),
  ]);

  return <Dashboard initialRules={rules} initialNotifications={notifications} />;
}
