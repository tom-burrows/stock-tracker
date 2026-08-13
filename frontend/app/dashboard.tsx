"use client";

import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import type { AlertCondition, AlertRule, Notification } from "@/lib/types";

const USER_ID = 1;

interface DashboardProps {
  initialRules: AlertRule[];
  initialNotifications: Notification[];
}

export default function Dashboard({ initialRules, initialNotifications }: DashboardProps) {
  const [rules, setRules] = useState(initialRules);
  const [notifications, setNotifications] = useState(initialNotifications);
  const [symbol, setSymbol] = useState("");
  const [condition, setCondition] = useState<AlertCondition>("PRICE_ABOVE");
  const [threshold, setThreshold] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const client = new Client({
      brokerURL: process.env.NEXT_PUBLIC_NOTIFICATION_WS_URL ?? "ws://localhost:8082/ws",
      onConnect: () => {
        client.subscribe("/topic/notifications", (message) => {
          const notification = JSON.parse(message.body) as Notification;
          if (notification.userId === USER_ID) {
            setNotifications((prev) => [notification, ...prev]);
          }
        });
      },
    });
    client.activate();
    return () => {
      client.deactivate();
    };
  }, []);

  async function handleCreateRule(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      const res = await fetch("/api/alert-rules", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId: USER_ID, symbol, condition, threshold: Number(threshold) }),
      });
      if (res.ok) {
        const created = (await res.json()) as AlertRule;
        setRules((prev) => [created, ...prev]);
        setSymbol("");
        setThreshold("");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleActive(id: number) {
    const res = await fetch(`/api/alert-rules/${id}/toggle-active`, { method: "PATCH" });
    if (res.ok) {
      const updated = (await res.json()) as AlertRule;
      setRules((prev) => prev.map((rule) => (rule.id === id ? updated : rule)));
    }
  }

  async function handleDelete(id: number) {
    const res = await fetch(`/api/alert-rules/${id}`, { method: "DELETE" });
    if (res.ok) {
      setRules((prev) => prev.filter((rule) => rule.id !== id));
    }
  }

  return (
    <div className="flex flex-1 flex-col gap-8 bg-zinc-50 p-8 dark:bg-black">
      <section className="mx-auto w-full max-w-2xl">
        <h1 className="mb-4 text-2xl font-semibold text-black dark:text-zinc-50">Alert rules</h1>

        <form onSubmit={handleCreateRule} className="mb-6 flex flex-wrap gap-2">
          <input
            type="text"
            placeholder="Symbol (e.g. BTC)"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            required
            className="rounded border border-black/[.08] bg-white px-3 py-2 text-black dark:border-white/[.145] dark:bg-zinc-900 dark:text-zinc-50"
          />
          <select
            value={condition}
            onChange={(e) => setCondition(e.target.value as AlertCondition)}
            className="rounded border border-black/[.08] bg-white px-3 py-2 text-black dark:border-white/[.145] dark:bg-zinc-900 dark:text-zinc-50"
          >
            <option value="PRICE_ABOVE">Price above</option>
            <option value="PRICE_BELOW">Price below</option>
          </select>
          <input
            type="number"
            step="any"
            placeholder="Threshold"
            value={threshold}
            onChange={(e) => setThreshold(e.target.value)}
            required
            className="rounded border border-black/[.08] bg-white px-3 py-2 text-black dark:border-white/[.145] dark:bg-zinc-900 dark:text-zinc-50"
          />
          <button
            type="submit"
            disabled={submitting}
            className="rounded bg-foreground px-4 py-2 text-background disabled:opacity-50"
          >
            Create
          </button>
        </form>

        <ul className="flex flex-col gap-2">
          {rules.map((rule) => (
            <li
              key={rule.id}
              className="flex items-center justify-between rounded border border-black/[.08] bg-white px-4 py-3 dark:border-white/[.145] dark:bg-zinc-900"
            >
              <span className="text-black dark:text-zinc-50">
                {rule.symbol} {rule.condition === "PRICE_ABOVE" ? ">" : "<"} {rule.threshold}{" "}
                <span className="text-sm text-zinc-500">{rule.active ? "(active)" : "(paused)"}</span>
              </span>
              <span className="flex gap-2">
                <button
                  onClick={() => handleToggleActive(rule.id)}
                  className="rounded border border-black/[.08] px-3 py-1 text-sm text-black dark:border-white/[.145] dark:text-zinc-50"
                >
                  {rule.active ? "Pause" : "Resume"}
                </button>
                <button
                  onClick={() => handleDelete(rule.id)}
                  className="rounded border border-black/[.08] px-3 py-1 text-sm text-black dark:border-white/[.145] dark:text-zinc-50"
                >
                  Delete
                </button>
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto w-full max-w-2xl">
        <h2 className="mb-4 text-2xl font-semibold text-black dark:text-zinc-50">Notifications</h2>
        <ul className="flex flex-col gap-2">
          {notifications.map((notification) => (
            <li
              key={notification.id}
              className="rounded border border-black/[.08] bg-white px-4 py-3 text-black dark:border-white/[.145] dark:bg-zinc-900 dark:text-zinc-50"
            >
              {notification.symbol} {notification.condition === "PRICE_ABOVE" ? "rose above" : "fell below"}{" "}
              {notification.threshold.toString()} (observed {notification.observedPrice.toString()})
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
