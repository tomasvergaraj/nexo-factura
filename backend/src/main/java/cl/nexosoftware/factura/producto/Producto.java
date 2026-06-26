package cl.nexosoftware.factura.producto;

import jakarta.persistence.*;
import lombok.*;

/** Item de catalogo. El precio se almacena como valor neto entero (CLP). */
@Entity
@Table(name = "producto", uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "codigo"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Long empresaId;

    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "precio_neto", nullable = false)
    private Long precioNeto;

    @Column(nullable = false)
    private String unidad;

    /** Si es false, el item es exento de IVA. */
    @Column(nullable = false)
    private boolean afecto;

    @Column(nullable = false)
    private boolean activo;

    /** Control de concurrencia optimista (evita el lost update en edicion). */
    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        this.activo = true;
        if (this.unidad == null) this.unidad = "UN";
    }
}
