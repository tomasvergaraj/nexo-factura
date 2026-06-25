import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { Landing } from "./pages/Landing";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/app/Dashboard";
import { Documentos } from "./pages/app/Documentos";
import { NuevaFactura } from "./pages/app/NuevaFactura";
import { Placeholder } from "./pages/app/Placeholder";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/ingresar" element={<Login />} />
        <Route path="/app" element={<Dashboard />} />
        <Route path="/app/documentos" element={<Documentos />} />
        <Route path="/app/nueva-factura" element={<NuevaFactura />} />
        <Route path="/app/clientes" element={<Placeholder titulo="Clientes" />} />
        <Route path="/app/productos" element={<Placeholder titulo="Productos" />} />
        <Route path="/app/folios" element={<Placeholder titulo="Folios (CAF)" />} />
        <Route path="/app/configuracion" element={<Placeholder titulo="Configuración" />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
