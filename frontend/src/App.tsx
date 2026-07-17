import type { ReactNode } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { Landing } from "./pages/Landing";
import { Login } from "./pages/Login";
import { Sobre } from "./pages/site/Sobre";
import { Contacto } from "./pages/site/Contacto";
import { Terminos } from "./pages/site/Terminos";
import { Privacidad } from "./pages/site/Privacidad";
import { Estado } from "./pages/site/Estado";
import { Dashboard } from "./pages/app/Dashboard";
import { Documentos } from "./pages/app/Documentos";
import { DetalleDocumento } from "./pages/app/DetalleDocumento";
import { NuevaFactura } from "./pages/app/NuevaFactura";
import { Clientes } from "./pages/app/Clientes";
import { Productos } from "./pages/app/Productos";
import { Folios } from "./pages/app/Folios";
import { Rcof } from "./pages/app/Rcof";
import { Compras } from "./pages/app/Compras";
import { Libros } from "./pages/app/Libros";
import { Configuracion } from "./pages/app/Configuracion";
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
        <Route path="/sobre" element={<Sobre />} />
        <Route path="/contacto" element={<Contacto />} />
        <Route path="/terminos" element={<Terminos />} />
        <Route path="/privacidad" element={<Privacidad />} />
        <Route path="/estado" element={<Estado />} />
        <Route path="/app" element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/app/documentos" element={<RequireAuth><Documentos /></RequireAuth>} />
        <Route path="/app/documentos/:id" element={<RequireAuth><DetalleDocumento /></RequireAuth>} />
        <Route path="/app/nueva-factura" element={<RequireAuth><NuevaFactura /></RequireAuth>} />
        <Route path="/app/clientes" element={<RequireAuth><Clientes /></RequireAuth>} />
        <Route path="/app/productos" element={<RequireAuth><Productos /></RequireAuth>} />
        <Route path="/app/folios" element={<RequireAuth><Folios /></RequireAuth>} />
        <Route path="/app/rcof" element={<RequireAuth><Rcof /></RequireAuth>} />
        <Route path="/app/compras" element={<RequireAuth><Compras /></RequireAuth>} />
        <Route path="/app/libros" element={<RequireAuth><Libros /></RequireAuth>} />
        <Route path="/app/configuracion" element={<RequireAuth><Configuracion /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
