import { SitePage, ProseSection } from "../../components/site/SitePage";

export function Terminos() {
  return (
    <SitePage
      titulo="Términos y condiciones"
      descripcion="Condiciones de uso del servicio Nexo Factura, operado por Nexo Software SpA."
    >
      <p className="text-sm text-slate-soft">Última actualización: 17 de julio de 2026</p>

      <ProseSection titulo="1. El servicio">
        <p>
          Nexo Factura es una plataforma de facturación electrónica que permite
          emitir Documentos Tributarios Electrónicos (DTE) conforme a la
          normativa del Servicio de Impuestos Internos de Chile (SII): facturas
          afectas y exentas, boletas y notas de crédito o débito, además de la
          gestión de folios (CAF), libros y reportes asociados.
        </p>
        <p>
          Al crear una cuenta o usar el servicio aceptas estos términos. Si los
          usas en representación de una empresa, declaras estar facultado para
          obligarla.
        </p>
      </ProseSection>

      <ProseSection titulo="2. Tu cuenta">
        <p>
          Eres responsable de mantener la confidencialidad de tus credenciales
          de acceso y de toda actividad realizada con tu cuenta. Debes
          notificarnos sin demora cualquier uso no autorizado.
        </p>
        <p>
          La información que registras (razón social, RUT, giro, direcciones,
          clientes y productos) debe ser veraz y estar actualizada: es la que se
          usa para construir tus documentos tributarios.
        </p>
      </ProseSection>

      <ProseSection titulo="3. Tu rol como emisor ante el SII">
        <p>
          Nexo Factura es un intermediario tecnológico. El emisor legal de cada
          DTE es tu empresa: los documentos se firman con tu certificado digital
          y se emiten con los folios (CAF) que el SII autorizó a tu empresa. La
          responsabilidad tributaria por el contenido, la oportunidad y la
          corrección de los documentos emitidos es tuya.
        </p>
        <p>
          Nos obligamos a construir, firmar y enviar los documentos con el
          formato exigido por el SII, a registrar la respuesta de ese organismo
          y a informarte el estado de cada envío.
        </p>
      </ProseSection>

      <ProseSection titulo="4. Planes y pagos">
        <p>
          Los precios vigentes se publican en el sitio y se expresan en pesos
          chilenos. El período de prueba no exige medio de pago. Puedes cambiar
          de plan o cancelar cuando quieras; la cancelación rige desde el
          siguiente período de facturación y no genera reembolsos
          proporcionales, salvo que la ley disponga otra cosa.
        </p>
      </ProseSection>

      <ProseSection titulo="5. Uso aceptable">
        <p>
          No puedes usar el servicio para emitir documentos con información
          falsa, para actividades ilícitas, ni intentar vulnerar la seguridad de
          la plataforma o acceder a datos de otras cuentas. Podemos suspender
          cuentas que infrinjan estas reglas, avisándote cuando sea posible.
        </p>
      </ProseSection>

      <ProseSection titulo="6. Disponibilidad y soporte">
        <p>
          Trabajamos para mantener el servicio disponible de forma continua,
          pero no garantizamos disponibilidad ininterrumpida: hay ventanas de
          mantención y dependencias de terceros (incluido el propio SII, cuyos
          períodos de contingencia se manejan según su normativa). Publicamos el
          estado de la plataforma en la página de estado del servicio.
        </p>
      </ProseSection>

      <ProseSection titulo="7. Tus datos y documentos">
        <p>
          Los datos y documentos que generas en la plataforma son tuyos.
          Mantenemos respaldos y conservamos tus DTE mientras tu cuenta esté
          activa y por los plazos de conservación que exige la normativa
          tributaria. Al cerrar tu cuenta puedes solicitar la exportación de tus
          documentos. El tratamiento de datos personales se rige por nuestra
          Política de Privacidad.
        </p>
      </ProseSection>

      <ProseSection titulo="8. Propiedad intelectual">
        <p>
          El software, la marca y el contenido del sitio son de Nexo Software
          SpA o de sus licenciantes. La suscripción te otorga un derecho de uso
          no exclusivo e intransferible del servicio, no una licencia sobre el
          software.
        </p>
      </ProseSection>

      <ProseSection titulo="9. Limitación de responsabilidad">
        <p>
          En la máxima medida permitida por la ley chilena, la responsabilidad
          total de Nexo Software SpA por daños derivados del uso del servicio se
          limita al monto pagado por el servicio en los tres meses anteriores al
          hecho que la origina. Nada en estos términos limita responsabilidades
          que la ley no permita limitar.
        </p>
      </ProseSection>

      <ProseSection titulo="10. Cambios a estos términos">
        <p>
          Podemos actualizar estos términos. Si el cambio es relevante, te lo
          avisaremos por correo o dentro de la plataforma con anticipación
          razonable. Seguir usando el servicio después de la fecha de vigencia
          implica aceptar la versión actualizada.
        </p>
      </ProseSection>

      <ProseSection titulo="11. Ley aplicable">
        <p>
          Estos términos se rigen por las leyes de la República de Chile.
          Cualquier controversia se someterá a los tribunales ordinarios de
          justicia competentes.
        </p>
        <p>
          ¿Dudas sobre estos términos? Escríbenos a{" "}
          <a href="mailto:contacto@nexosoftware.cl" className="font-medium text-cobalt transition-colors hover:text-cobalt-dark">
            contacto@nexosoftware.cl
          </a>.
        </p>
      </ProseSection>
    </SitePage>
  );
}
