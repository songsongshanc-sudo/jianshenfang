import { request } from "./http";
import type { CreatedOrder } from "./order";

export interface VisitConfig {
  enabled: boolean;
  depositFen: number;
  durationMinutes: number;
  validDays: number;
  warnMinutes: number;
  notice: string;
}

export interface MyVisit {
  id: string;
  orderId: string;
  status: string;
  depositFen: number;
  durationMinutes: number;
  warnMinutes: number;
  validUntil: string | null;
  enteredAt: string | null;
  dueExitAt: string | null;
  exitedAt: string | null;
  storeId: string | null;
  storeName: string | null;
  remainMs: number;
  warn: boolean;
  refundError: string | null;
}

export interface VisitOffer {
  config: VisitConfig;
  canBuy: boolean;
  blockReason: string | null;
  mine: MyVisit | null;
  member: boolean;
}

export function visitOffer(storeId?: string) {
  const q = storeId ? `?storeId=${encodeURIComponent(storeId)}` : "";
  return request<VisitOffer>({ url: `/api/mp/visit/offer${q}` });
}

export function createVisitOrder(storeId: string, idempotencyKey: string) {
  return request<CreatedOrder>({
    url: "/api/mp/visit/orders",
    method: "POST",
    header: { "Idempotency-Key": idempotencyKey, "Content-Type": "application/json" },
    data: { storeId },
  });
}

export function refundVisit() {
  return request<MyVisit>({ url: "/api/mp/visit/refund", method: "POST" });
}

export function mockVisitPay(orderId: string) {
  return request({ url: `/api/mp/visit/orders/${orderId}/mock-pay`, method: "POST" });
}
