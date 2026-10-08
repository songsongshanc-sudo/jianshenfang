import axios from "axios";
import { ElMessage } from "element-plus";
import { useSessionStore } from "../stores/session";

export interface ApiBody<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
}

export const http = axios.create({
  baseURL: "/",
  timeout: 15000
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem("adminToken");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiBody<unknown>;
    if (body && typeof body.code === "number" && body.code !== 0) {
      ElMessage.error(body.message || "请求失败");
      return Promise.reject(body);
    }
    return response;
  },
  (error) => {
    const message = error?.response?.data?.message || "网络异常";
    if (error?.response?.status === 401) {
      useSessionStore().clear();
    }
    ElMessage.error(message);
    return Promise.reject(error);
  }
);
