import { SitePage, ProseSection } from "../../components/site/SitePage";

export function Privacidad() {
  return (
    <SitePage
      titulo="Política de privacidad"
      descripcion="Cómo Nexo Software SpA trata los datos personales que pasan por Nexo Factura."
    >
      <p className="text-sm text-slate-soft">Última actualización: 17 de julio de 2026</p>

      <ProseSection titulo="1. Responsable del tratamiento">
        <p>
          El responsable es Nexo Software SpA, con domicilio en Quillota, Región
          de Valparaíso, Chile. Para cualquier solicitud sobre tus datos escribe
          a{" "}
          <a href="mailto:contacto@nexosoftware.cl" className="font-medium text-cobalt transition-colors hover:text-cobalt-dark">
            contacto@nexosoftware.cl
          </a>.
        </p>
      </ProseSection>

      <ProseSection titulo="2. Qué datos tratamos">
        <p>Tratamos tres tipos de datos:</p>
        <ul className="list-disc space-y-2 pl-5">
          <li>
            <strong className="font-medium text-ink-soft">De tu cuenta:</strong>{" "}
            nombre, correo, empresa asociada y credenciales (la contraseña se
            almacena cifrada de forma irreversible).
          </li>
          <li>
            <strong className="font-medium text-ink-soft">De tus documentos:</strong>{" "}
            los datos que la ley exige en un DTE — RUT, razón social, giro y
            dirección del emisor y del receptor, y el detalle de lo facturado.
          </li>
          <li>
            <strong className="font-medium text-ink-soft">De uso:</strong>{" "}
            registros técnicos (fechas de acceso, direcciones IP, acciones
            relevantes) necesarios para la seguridad y el soporte.
          </li>
        </ul>
      </ProseSection>

      <ProseSection titulo="3. Para qué los usamos">
        <p>
          Exclusivamente para prestar el servicio: emitir y gestionar tus DTE,
          enviarlos al SII, generar libros y reportes, darte soporte y cumplir
          obligaciones legales. No vendemos datos personales ni los usamos para
          publicidad de terceros.
        </p>
      </ProseSection>

      <ProseSection titulo="4. Base legal">
        <p>
          Tratamos los datos para ejecutar el contrato de servicio contigo y
          cumplir obligaciones legales, conforme a la Ley 19.628 sobre
          protección de la vida privada y a la Ley 21.719, que la modifica
          sustancialmente y cuya reforma entra en vigencia en diciembre de 2026.
          Los datos de receptores de documentos se tratan porque la normativa
          tributaria exige incluirlos en cada DTE.
        </p>
      </ProseSection>

      <ProseSection titulo="5. Con quién se comparten">
        <p>
          Con el SII, porque el envío del DTE es parte del servicio; y con
          proveedores de infraestructura (alojamiento y respaldo) que tratan los
          datos por cuenta nuestra y bajo obligaciones de confidencialidad. No
          hay otras cesiones, salvo requerimiento legal de autoridad
          competente.
        </p>
      </ProseSection>

      <ProseSection titulo="6. Cuánto tiempo se conservan">
        <p>
          Los datos de tu cuenta, mientras esté activa. Los documentos
          tributarios y sus respaldos, por los plazos de conservación que exige
          la normativa tributaria chilena (como regla general, seis años). Los
          registros técnicos se conservan por períodos acotados y se eliminan o
          anonimizan después.
        </p>
      </ProseSection>

      <ProseSection titulo="7. Tus derechos">
        <p>
          Puedes solicitar acceso, rectificación, supresión, oposición y
          portabilidad de tus datos personales escribiendo al correo del
          responsable. Respondemos dentro de los plazos legales. Ten presente
          que los datos contenidos en DTE ya emitidos no pueden alterarse: son
          documentos tributarios inmutables por exigencia normativa.
        </p>
      </ProseSection>

      <ProseSection titulo="8. Seguridad">
        <p>
          Aplicamos medidas técnicas y organizativas razonables: cifrado en
          tránsito, contraseñas con hash, control de acceso por empresa, sellos
          de integridad sobre los documentos y respaldos periódicos. Ningún
          sistema es infalible; si detectamos una brecha que te afecte, te lo
          comunicaremos conforme a la ley.
        </p>
      </ProseSection>

      <ProseSection titulo="9. Cookies y almacenamiento local">
        <p>
          El sitio no usa cookies de publicidad ni de seguimiento de terceros.
          La aplicación guarda en tu navegador únicamente lo necesario para
          mantener tu sesión iniciada.
        </p>
      </ProseSection>

      <ProseSection titulo="10. Cambios a esta política">
        <p>
          Si modificamos esta política te lo avisaremos por correo o dentro de
          la plataforma. La versión vigente estará siempre publicada en esta
          página con su fecha de actualización.
        </p>
      </ProseSection>
    </SitePage>
  );
}
