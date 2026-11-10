package control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import model.CompraModel.CompraRegistradaEvent;
import model.UsuarioModel.UsuarioRegistradoEvent;

/**
 * =====================================================================
 * EmailNotificationController — Servicio INTERNO de notificaciones.
 * ---------------------------------------------------------------------
 * Transversal: HU-07 (bienvenida al registrarse) · HU-05 (orden generada).
 *
 * ⚠ NO es un @RestController: no expone ningún endpoint. El frontend/usuario
 * NUNCA puede invocar el envío de correos. Solo el backend lo dispara.
 *
 * ¿Cómo se desacopla?
 *   • Los servicios de negocio publican EVENTOS de dominio
 *     (UsuarioRegistradoEvent, CompraRegistradaEvent) y no saben que existe
 *     el correo.
 *   • Esta clase los escucha con @TransactionalEventListener(AFTER_COMMIT):
 *     el correo sale SOLO si la operación quedó confirmada en la BD.
 *   • @Async: el envío corre en otro hilo; la respuesta HTTP no espera al
 *     servidor SMTP (RNF < 2 s).
 *   • Si el correo falla, se registra en el log y la operación de negocio
 *     NO se revierte (ya está confirmada).
 *
 * SOLID:
 *   • SRP — esta clase decide QUÉ notificar; {@link NotificacionService}
 *     decide CÓMO enviarlo.
 *   • DIP/OCP — depende de la interfaz NotificacionService; cambiar SMTP por
 *     otro canal (SMS, push) = nueva implementación, sin tocar esta clase.
 * =====================================================================
 */
@Component
public class EmailNotificationController {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationController.class);

    private final NotificacionService notificador;

    EmailNotificationController(NotificacionService notificador) {
        this.notificador = notificador;
    }

    /** Correo de bienvenida tras registrar la cuenta (HU-07). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alRegistrarUsuario(UsuarioRegistradoEvent e) {
        notificador.enviar(e.correo(), "Bienvenido a CampusMarket",
                "Hola " + e.nombre() + ",\n\nTu cuenta en CampusMarket (UCAB) fue creada con éxito.\n"
                + "Ya puedes explorar el catálogo y realizar tus compras en bolívares.\n\nEquipo CampusMarket");
    }

    /** Correo de confirmación de orden "Pendiente de Pago" (HU-05). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alRegistrarCompra(CompraRegistradaEvent e) {
        StringBuilder cuerpo = new StringBuilder()
                .append("Hola ").append(e.nombre()).append(",\n\n")
                .append("Registramos tu orden N° ").append(e.numeroOrden())
                .append(" con estado \"Pendiente de Pago\".\n\nDetalle:\n");
        e.detalles().forEach(d -> cuerpo.append(" - ").append(d.cantidad()).append(" x ").append(d.nombre())
                .append(": Bs. ").append(d.subtotal()).append('\n'));
        cuerpo.append("\nTotal: Bs. ").append(e.total()).append("\n\nEquipo CampusMarket");
        notificador.enviar(e.correo(), "Orden " + e.numeroOrden() + " registrada", cuerpo.toString());
        log.debug("Notificación de orden {} procesada", e.numeroOrden());
    }
}

// =====================================================================
// Abstracción del canal de envío (DIP)
// =====================================================================

/** Contrato mínimo para enviar una notificación de texto. */
interface NotificacionService {
    void enviar(String destinatario, String asunto, String cuerpo);
}

/**
 * Implementación SMTP. Si no hay servidor configurado (spring.mail.host vacío),
 * Spring no crea JavaMailSender y el mensaje solo se escribe en el log:
 * así el proyecto funciona en desarrollo sin credenciales de correo.
 */
@Component
class SmtpNotificacionService implements NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(SmtpNotificacionService.class);

    private final ObjectProvider<JavaMailSender> mailSender; // Opcional: puede no existir
    private final String remitente;

    SmtpNotificacionService(ObjectProvider<JavaMailSender> mailSender,
                            @Value("${campusmarket.mail.remitente}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String destinatario, String asunto, String cuerpo) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[CORREO-SIMULADO] Para: {} | Asunto: {}\n{}", destinatario, asunto, cuerpo);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(remitente);
            msg.setTo(destinatario);
            msg.setSubject(asunto);
            msg.setText(cuerpo);
            sender.send(msg);
        } catch (MailException ex) {
            // El fallo del correo nunca deshace la operación de negocio ya confirmada.
            log.warn("No se pudo enviar el correo a {}: {}", destinatario, ex.getMessage());
        }
    }
}
