import type { ReactNode } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { Landing } from "./pages/Landing";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/app/Dashboard";
import { Documentos } from "./pages/app/Documentos";
import { NuevaFactura } from "./pages/app/NuevaFactura";
import { Placeholder } from "./pages/app/Placeholder";
import { obtenerToken, RUTA_LOGIN } from "./lib/auth";

// Protege las rutas privadas: sin token, redirige al login.
function RequireAuth({ children }: { children: ReactNode }) {
  if (!obtenerToken()) {
    return <Navigate to={RUTA_LOGIN} replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/ingresar" element={<Login />} />
        <Route path="/app" element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/app/documentos" element={<RequireAuth><Documentos /></RequireAuth>} />
        <Route path="/app/nueva-factura" element={<RequireAuth><NuevaFactura /></RequireAuth>} />
        <Route path="/app/clientes" element={<RequireAuth><Placeholder titulo="Clientes" /></RequireAuth>} />
        <Route path="/app/productos" element={<RequireAuth><Placeholder titulo="Productos" /></RequireAuth>} />
        <Route path="/app/folios" element={<RequireAuth><Placeholder titulo="Folios (CAF)" /></RequireAuth>} />
        <Route path="/app/configuracion" element={<RequireAuth><Placeholder titulo="Configuración" /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
