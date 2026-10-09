/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_AMAP_KEY?: string;
  readonly VITE_AMAP_SECURITY_JS_CODE?: string;
}

interface Window {
  _AMapSecurityConfig?: { securityJsCode: string };
  AMapLoader?: {
    load: (options: { key: string; version: string }) => Promise<unknown>;
  };
}

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<object, object, unknown>;
  export default component;
}
