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
