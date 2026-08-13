export type AlertCondition = "PRICE_ABOVE" | "PRICE_BELOW";

export interface AlertRule {
  id: number;
  userId: number;
  symbol: string;
  condition: AlertCondition;
  threshold: number;
  active: boolean;
  lastTriggeredAt: string | null;
  createdAt: string;
}

export interface Notification {
  id: number;
  ruleId: number;
  userId: number;
  symbol: string;
  condition: AlertCondition;
  threshold: number;
  observedPrice: number;
  triggeredAt: string;
  createdAt: string;
}
