// Manejo del access token + refresh token en el navegador.
// Nota: limpiarSesion() solo borra el almacenamiento local; el cierre de sesion
// que ademas revoca el refresh token en el servidor vive en api.ts (cerrarSesion),
// para no crear un ciclo de imports (auth.ts no debe importar api.ts).

const TOKEN_KEY = "nf_token";
const REFRESH_KEY = "nf_refresh";
const USUARIO_KEY = "nf_usuario";

export interface UsuarioSesion {
  id: number;
  nombre: string;
  email: string;
  rol: string;
  empresaId: number | null;
}

export function guardarSesion(token: string, refreshToken: string, usuario: UsuarioSesion) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(REFRESH_KEY, refreshToken);
  localStorage.setItem(USUARIO_KEY, JSON.stringify(usuario));
}

/** Actualiza solo los tokens (tras una rotacion via /refresh). */
export function guardarTokens(token: string, refreshToken: string) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function obtenerToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function obtenerRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

export function obtenerUsuario(): UsuarioSesion | null {
  const raw = localStorage.getItem(USUARIO_KEY);
  return raw ? (JSON.parse(raw) as UsuarioSesion) : null;
}

/** Borra el estado de sesion local (sin tocar el servidor). */
export function limpiarSesion() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USUARIO_KEY);
}

// Ruta del login (única fuente de verdad para redirecciones).
export const RUTA_LOGIN = "/ingresar";

// Devuelve el empresaId de la sesión actual o lanza un error controlado
// si no hay sesión o el usuario no tiene empresa asociada.
export function empresaIdActual(): number {
  const usuario = obtenerUsuario();
  if (!usuario || usuario.empresaId == null) {
    throw new Error("La sesión no tiene una empresa asociada.");
  }
  return usuario.empresaId;
}
