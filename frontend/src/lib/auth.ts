// Manejo simple del token JWT en el navegador.

const TOKEN_KEY = "nf_token";
const USUARIO_KEY = "nf_usuario";

export interface UsuarioSesion {
  id: number;
  nombre: string;
  email: string;
  rol: string;
  empresaId: number | null;
}

export function guardarSesion(token: string, usuario: UsuarioSesion) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USUARIO_KEY, JSON.stringify(usuario));
}

export function obtenerToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function obtenerUsuario(): UsuarioSesion | null {
  const raw = localStorage.getItem(USUARIO_KEY);
  return raw ? (JSON.parse(raw) as UsuarioSesion) : null;
}

export function cerrarSesion() {
  localStorage.removeItem(TOKEN_KEY);
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
