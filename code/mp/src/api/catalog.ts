import { request } from "./http";

export interface PublicStore {
  id: string;
  name: string;
  province: string;
  city: string;
  address: string;
  longitude: number;
  latitude: number;
  coverUrl: string | null;
  businessHours: string;
  status: string;
  distanceMeters: number | null;
  onlineLocked: boolean;
  onlineText: string;
}

export interface GuideStep {
  id: string;
  imageUrl: string;
  caption: string;
  sortNo: number;
}

export interface Contacts {
  shift: string;
  nightAvailable: boolean;
  nightHint: string | null;
  servicePhones: string[];
  logisticsPhones: string[];
  complaintPhones: string[];
  complaintHint: string | null;
}

export interface BannerItem {
  id: string;
  title: string;
  imageUrl: string;
  linkUrl: string | null;
}

export interface NoticeItem {
  id: string;
  content: string;
}

export function listStores(query: { province?: string; city?: string; longitude?: number; latitude?: number }) {
  const parts: string[] = [];
  if (query.province) parts.push(`province=${encodeURIComponent(query.province)}`);
  if (query.city) parts.push(`city=${encodeURIComponent(query.city)}`);
  if (query.longitude != null && query.latitude != null) {
    parts.push(`longitude=${query.longitude}`);
    parts.push(`latitude=${query.latitude}`);
  }
  const suffix = parts.length ? `?${parts.join("&")}` : "";
  return request<PublicStore[]>({ url: `/api/mp/stores${suffix}` });
}

export function storeDetail(id: string, longitude?: number, latitude?: number) {
  const suffix =
    longitude != null && latitude != null ? `?longitude=${longitude}&latitude=${latitude}` : "";
  return request<PublicStore>({ url: `/api/mp/stores/${id}${suffix}` });
}

export function storeContacts(id: string) {
  return request<Contacts>({ url: `/api/mp/stores/${id}/contacts` });
}

export function storeGuides(id: string) {
  return request<GuideStep[]>({ url: `/api/mp/stores/${id}/guides` });
}

export function banners() {
  return request<BannerItem[]>({ url: "/api/mp/banners" });
}

export function notices() {
  return request<NoticeItem[]>({ url: "/api/mp/notices" });
}

export interface CardItem {
  id: string;
  name: string;
  priceFen: number;
  displayText: string | null;
  validDays: number;
  remaining: number | null;
  purchasable: boolean;
  lockText: string | null;
}

export interface MeProfile {
  registerStatus: string;
  nickname: string | null;
  memberNo: string | null;
  companionDays: number;
  consecutiveDays: number;
  cumulativeDays: number;
  consecutiveRemain: number | null;
  storeMember: boolean;
  faceSync: string;
}

export function cards(storeId: string, placement: "HOME" | "ALL") {
  return request<CardItem[]>({ url: `/api/mp/cards?storeId=${storeId}&placement=${placement}` });
}

export function me(storeId?: string) {
  const suffix = storeId ? `?storeId=${storeId}` : "";
  return request<MeProfile>({ url: `/api/mp/me${suffix}` });
}

export function yuan(fen: number) {
  const value = fen / 100;
  return Number.isInteger(value) ? `¥${value}` : `¥${value.toFixed(2)}`;
}

export function formatDistance(meters: number | null) {
  if (meters == null) return "";
  if (meters >= 1000) return `${(meters / 1000).toFixed(1)} km`;
  return `${meters} m`;
}
