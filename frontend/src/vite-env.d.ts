/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Si es "true", la app funciona con datos mock sin backend. Default: false. */
  readonly VITE_USE_MOCK?: string;
  /** Base URL de la API. Default: "/api". */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
