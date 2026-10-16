import { request } from "./http";

export type BookingOffer = {
  packId: string;
  packName: string;
  coachId: string;
  coachName: string;
  storeId: string;
  roomCols: number;
  roomRows: number;
  minutesPerLesson: number;
  remaining: number;
  startHms: string[];
  minDate: string;
  maxDate: string;
};

export type BookingDetail = {
  id: string;
  packId: string;
  packName: string;
  coachId: string;
  coachName: string;
  storeId: string;
  classDate: string;
  startHm: string;
  startAt: string;
  endAt: string;
  seatNo: string;
  seatRow: number;
  seatCol: number;
  status: string;
  rejectReason: string | null;
  cancelReason: string | null;
  pendingUntil: string | null;
  actedBy?: string | null;
  actedByLabel?: string | null;
  pastStart: boolean;
  userId: string;
  userName: string;
  memberNo: string;
  avatarUrl: string | null;
  phone?: string | null;
  appeals: Array<{
    id: string;
    reason: string;
    status: string;
    rejectReason: string | null;
    createdAt: string;
    resolvedAt: string | null;
  }>;
};

export function bookingOffer(packId: string) {
  return request<BookingOffer>({ url: `/api/mp/packs/${packId}/booking-offer`, method: "GET" });
}

export function occupiedSeats(packId: string, date: string, startHm: string) {
  return request<string[]>({
    url: `/api/mp/packs/${packId}/occupied-seats?date=${encodeURIComponent(date)}&startHm=${encodeURIComponent(startHm)}`,
    method: "GET",
  });
}

export function createBooking(body: {
  packId: string;
  classDate: string;
  startHm: string;
  seatRow: number;
  seatCol: number;
}) {
  return request<BookingDetail>({ url: "/api/mp/lesson-bookings", method: "POST", data: body });
}

export function myBookings() {
  return request<BookingDetail[]>({ url: "/api/mp/lesson-bookings", method: "GET" });
}

export function bookingDetail(id: string) {
  return request<BookingDetail>({ url: `/api/mp/lesson-bookings/${id}`, method: "GET" });
}

export function cancelBooking(id: string) {
  return request<BookingDetail>({ url: `/api/mp/lesson-bookings/${id}/cancel`, method: "POST" });
}

export function createAppeal(id: string, reason: string) {
  return request<BookingDetail>({ url: `/api/mp/lesson-bookings/${id}/appeals`, method: "POST", data: { reason } });
}

export function coachBookings() {
  return request<BookingDetail[]>({ url: "/api/mp/coach/lesson-bookings", method: "GET" });
}

export function coachBookingDetail(id: string) {
  return request<BookingDetail>({ url: `/api/mp/coach/lesson-bookings/${id}`, method: "GET" });
}

export function coachApprove(id: string) {
  return request<BookingDetail>({ url: `/api/mp/coach/lesson-bookings/${id}/approve`, method: "POST" });
}

export function coachReject(id: string, reason: string) {
  return request<BookingDetail>({
    url: `/api/mp/coach/lesson-bookings/${id}/reject`,
    method: "POST",
    data: { reason },
  });
}

export function coachRefund(id: string) {
  return request<BookingDetail>({ url: `/api/mp/coach/lesson-bookings/${id}/refund`, method: "POST" });
}

export function coachRejectAppeal(id: string, reason: string) {
  return request<BookingDetail>({
    url: `/api/mp/coach/lesson-bookings/${id}/reject-appeal`,
    method: "POST",
    data: { reason },
  });
}
