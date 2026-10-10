import { request } from "./http";

export interface AgreementDoc {
  id: string;
  title: string;
  versionNo: number;
  content: string;
}

export interface CreatedOrder {
  orderId: string;
  orderNo: string;
  amountFen: number;
  status: string;
  mockPay: boolean;
  timeStamp?: string;
  nonceStr?: string;
  payPackage?: string;
  signType?: string;
  paySign?: string;
}

export interface OrderRow {
  id: string;
  orderNo: string;
  storeName: string;
  productName: string;
  amountFen: number;
  status: string;
  paidAt: string | null;
}

export function currentAgreement() {
  return request<AgreementDoc | null>({ url: "/api/mp/agreements/current" });
}

export function createOrder(body: {
  storeId: string;
  cardProductId: string;
  agreementId: string;
  agreementVersion: number;
}, idempotencyKey: string) {
  return request<CreatedOrder>({
    url: "/api/mp/orders",
    method: "POST",
    header: { "Idempotency-Key": idempotencyKey, "Content-Type": "application/json" },
    data: body,
  });
}

export function mockPay(orderId: string) {
  return request<OrderRow>({ url: `/api/mp/orders/${orderId}/mock-pay`, method: "POST" });
}

export function orderDetail(orderId: string) {
  return request<OrderRow>({ url: `/api/mp/orders/${orderId}` });
}

export function myOrders() {
  return request<OrderRow[]>({ url: "/api/mp/orders" });
}

export function cancelOrder(orderId: string) {
  return request<OrderRow>({ url: `/api/mp/orders/${orderId}/cancel`, method: "POST" });
}

export function requestWxPay(created: CreatedOrder): Promise<void> {
  return new Promise((resolve, reject) => {
    if (!created.timeStamp || !created.nonceStr || !created.payPackage || !created.paySign) {
      reject(new Error("支付参数不完整"));
      return;
    }
    uni.requestPayment({
      provider: "wxpay",
      orderInfo: {},
      timeStamp: created.timeStamp,
      nonceStr: created.nonceStr,
      package: created.payPackage,
      signType: created.signType || "RSA",
      paySign: created.paySign,
      success: () => resolve(),
      fail: (err) => reject(err),
    });
  });
}

export async function waitUntilPaid(orderId: string, tries = 12): Promise<OrderRow> {
  let last: OrderRow | null = null;
  for (let i = 0; i < tries; i++) {
    last = await orderDetail(orderId);
    if (last.status === "PAID") {
      return last;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  if (!last) {
    throw new Error("查询订单失败");
  }
  return last;
}

export function orderStatusText(status: string) {
  if (status === "PAID") return "已支付";
  if (status === "CLOSED") return "已关闭";
  if (status === "REFUNDED") return "已退款";
  return "待支付";
}
