import { request } from "./http";

export function wifi(storeId: string) {
  return request<{ ssid: string | null; password: string | null }>({ url: `/api/mp/stores/${storeId}/wifi`, method: "GET" });
}

export function createRepair(storeId: string, content: string, equipmentCode: string) {
  return request<{ id: string }>({ url: "/api/mp/repairs", method: "POST", data: { storeId, content, equipmentCode } });
}

export function myRepairs() {
  return request<Array<{ id: string; content: string; status: string }>>({ url: "/api/mp/repairs", method: "GET" });
}

export function createComplaint(storeId: string, category: string, content: string) {
  return request<{ id: string }>({ url: "/api/mp/complaints", method: "POST", data: { storeId, category, content } });
}

export function myComplaints() {
  return request<Array<{ id: string; content: string; status: string }>>({ url: "/api/mp/complaints", method: "GET" });
}

export function createLost(storeId: string, kind: string, name: string, content: string, visible: boolean) {
  return request<{ id: string }>({ url: "/api/mp/lost", method: "POST", data: { storeId, kind, name, content, visible } });
}

export function lostFeed(storeId: string, tab: string) {
  return request<Array<{ id: string; name: string; content: string; kind: string }>>({
    url: `/api/mp/lost?storeId=${storeId}&tab=${tab}`, method: "GET"
  });
}

export function equipmentList(storeId: string) {
  return request<Array<{ id: string; code: string; name: string; intro: string; video_url: string }>>({
    url: `/api/mp/equipment?storeId=${storeId}`, method: "GET"
  });
}

export function equipmentByCode(storeId: string, code: string) {
  return request<{ name: string; intro: string; video_url: string }>({
    url: `/api/mp/equipment/by-code?storeId=${storeId}&code=${encodeURIComponent(code)}`, method: "GET"
  });
}

export function redeemGroupon(storeId: string, platform: string, code: string) {
  return request<{ id: string }>({ url: "/api/mp/groupon/redeem", method: "POST", data: { storeId, platform, code } });
}

export function reviews(platform: string) {
  return request<unknown[]>({ url: `/api/mp/reviews?platform=${platform}`, method: "GET" });
}

export function studentStatus() {
  return request<{ status: string }>({ url: "/api/mp/student", method: "GET" });
}

export interface CoachListItem {
  id: string;
  name: string;
  intro: string;
  phone?: string;
  avatar_url?: string | null;
  photo_url?: string | null;
  specialty?: string | null;
  rating?: number | null;
  lesson_taught?: number | null;
  certificate_urls?: string | null;
  min_price_fen?: number | null;
  pack_count?: number | null;
  store_cover_url?: string | null;
  store_name?: string | null;
}

export interface CoursePack {
  id: string;
  coach_id: string;
  store_id: string;
  name: string;
  price_fen: number;
  lesson_count: number;
  content: string | null;
  audience: string | null;
  cover_url?: string | null;
  minutes_per_lesson?: number | null;
  status?: string;
  coach_name?: string;
  coach_photo_url?: string | null;
  coach_avatar_url?: string | null;
}

export interface CoachDetail extends CoachListItem {
  store_id: string;
  packs: CoursePack[];
}

export function coaches(storeId: string) {
  return request<CoachListItem[]>({ url: `/api/mp/coaches?storeId=${storeId}`, method: "GET" });
}

export function coachDetail(id: string) {
  return request<CoachDetail>({ url: `/api/mp/coaches/${id}`, method: "GET" });
}

export function packs(storeId: string) {
  return request<CoursePack[]>({ url: `/api/mp/packs?storeId=${storeId}`, method: "GET" });
}

export function packDetail(id: string) {
  return request<CoursePack>({ url: `/api/mp/packs/${id}`, method: "GET" });
}

export function coachMe() {
  return request<{ coach: boolean; id?: string; name?: string; storeId?: string }>({
    url: "/api/mp/coach/me",
    method: "GET",
  });
}

export function myLessons() {
  return request<Array<{ pack_id: string; name: string; remaining: number }>>({ url: "/api/mp/lessons", method: "GET" });
}

export function lessonQr(packId: string) {
  return request<{ token: string }>({ url: "/api/mp/lessons/qr", method: "POST", data: { packId } });
}

export function redeemLesson(token: string) {
  return request<void>({ url: "/api/mp/lessons/redeem", method: "POST", data: { token } });
}

export function franchisePage() {
  return request<{ intro: string; hotline: string; points: string; support: string; steps: string }>({ url: "/api/mp/franchise", method: "GET" });
}

export function franchiseLead(body: { name: string; phone: string; budget: string; province: string; city: string }) {
  return request<{ id: string }>({ url: "/api/mp/franchise/leads", method: "POST", data: body });
}

export function messages() {
  return request<Array<{ id: string; title: string; content: string; read_at: string | null }>>({ url: "/api/mp/messages", method: "GET" });
}

export function readMessage(id: string) {
  return request<void>({ url: `/api/mp/messages/${id}/read`, method: "POST" });
}
