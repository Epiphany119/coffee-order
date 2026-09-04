/// <reference types="vite/client" />
/// <reference path="./env.d.ts" />

declare module '*.png' {
  const value: string
  export default value
}
