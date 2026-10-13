/// <reference types="vite/client" />

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/ban-types
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'uqrcodejs' {
  interface UQRCode {
    data: string
    size: number
    modules: Array<Array<{ isBlack: boolean }>>
    make(): void
    getDrawModules(): Array<Record<string, unknown>>
  }
  const UQRCode: {
    new (): UQRCode
  }
  export default UQRCode
}
