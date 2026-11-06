# Notification Library

Librería Java 21 para enviar notificaciones por Email, SMS, Push y Slack con una misma operación. La aplicación configura los proveedores en Java, elige uno predeterminado por canal y puede usar otro para un envío puntual. Se distribuye como un JAR sin dependencias externas de ejecución; funciona con o sin framework.

**Todos los proveedores son simulados.** Un recibo indica que el adaptador aceptó la solicitud según el protocolo modelado. No se hace HTTP ni se confirma la entrega al destinatario.

## Probar en un minuto

Con JDK 21 y Maven, desde la raíz:

```sh
mvn clean verify
java --class-path target/notification-library-0.1.0-SNAPSHOT.jar examples/MultiProviderEmailDemo.java
```

La demo envía el mismo email tres veces: primero por SendGrid, luego por Mailgun y nuevamente por SendGrid. Así se comprueba que elegir un proveedor para una llamada conserva el default.

**Alternativa con Docker** (contenedores Linux, sin instalar Java localmente):

```sh
docker build -t notification-library:demo .
docker run --rm --network none notification-library:demo
docker run --rm --network none notification-library:demo MultiProviderEmailDemo
```

El comando sin argumentos ejecuta EmailDemo. El build inicial necesita acceso a las imágenes base y dependencias de Maven; las demos funcionan sin red ni credenciales reales.

## Instalar en otro proyecto

Ejecutá `mvn clean install` en este repositorio para instalar el SNAPSHOT en tu repositorio Maven local. En el POM de la aplicación agregá:

```xml
<dependency>
    <groupId>dev.challenge</groupId>
    <artifactId>notification-library</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Con Gradle, habilitá `mavenLocal()` en los repositorios del consumidor y agregá `implementation("dev.challenge:notification-library:0.1.0-SNAPSHOT")`.

## Primer envío

```java
import dev.challenge.notifications.adapters.sendgrid.*;
import dev.challenge.notifications.channels.email.*;
import dev.challenge.notifications.configuration.Notifications;
import dev.challenge.notifications.domain.ProviderId;

class PrimerEnvio {
    public static void main(String[] args) {
        var id = new ProviderId("sendgrid");
        var config = new SendGridConfig(id, "FAKE_KEY", new EmailAddress("sender@example.com"));
        var cliente = Notifications.builder()
                .register(EmailNotification.class, id,
                        new SimulatedSendGridSender(config, SendGridSimulation.ACCEPTED))
                .defaultProvider(EmailNotification.class, id)
                .build();

        var email = new EmailNotification(new EmailAddress("demo@example.com"), "Alta", "Hola");
        var recibo = cliente.send(email);
        System.out.println(recibo.providerId()); // sendgrid
    }
}
```

Para elegir otro proveedor, registrá un segundo sender para `EmailNotification.class` con su propio ID y llamá a `cliente.send(email, otroId)`. El ID de la configuración debe coincidir con el del registro. No se elige un proveedor por orden de registro ni se hace fallback silencioso.

## Canales y proveedores

| Canal y proveedor | Configuración Java (además del ProviderId) | Qué cuenta como aceptación simulada | Ejemplo completo |
|---|---|---|---|
| Email · SendGrid | Clave y dirección remitente | 202, sin ID remoto requerido | [Email](examples/EmailDemo.java) |
| Email · Mailgun | Dominio, clave y remitente | 200 con ID y mensaje no blancos | [Dos proveedores](examples/MultiProviderEmailDemo.java) |
| SMS · Twilio | SID de cuenta, SID de clave, secreto y teléfono remitente | 201 con SID no blanco y estado queued | [SMS y Push](examples/MobileDemo.java) |
| Push · FCM | Proyecto y Supplier de token de acceso por intento | 200 con nombre de mensaje del proyecto, sin código de error contradictorio | [SMS y Push](examples/MobileDemo.java) |
| Slack · incoming webhook | URL secreta que fija el destino | 200 con cuerpo exacto `ok`; sin ID remoto | [Slack](examples/SlackDemo.java) |

Email tiene asunto y cuerpo, SMS sólo cuerpo, Push tiene título y cuerpo, y Slack sólo texto. La validación del teléfono comprueba la forma E.164 al construir el destinatario. Para consultar los protocolos que modelan los simuladores, ver [SendGrid Mail Send](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send), [Mailgun Messages](https://documentation.mailgun.com/docs/mailgun/api-reference/send/mailgun/messages/post-v3--domain-name--messages), [Twilio Messages](https://www.twilio.com/docs/messaging/api/message-resource), [FCM HTTP v1](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages/send) y [Slack incoming webhooks](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/).

## API y errores

| Entrada | Contrato |
|---|---|
| `Notifications.builder().register(...).defaultProvider(...).build()` | Registra el tipo exacto y sus estrategias; construye un cliente con una copia inmutable de las rutas. |
| `NotificationClient.send(n)` / `send(n, id)` | Envía por default o por selección explícita. Devuelve un `SendReceipt` con proveedor, ID remoto opcional y marca de simulación. |
| `NotificationSender<N>.send(n)` | Puerto que implementa cada proveedor. Debe devolver un recibo válido o lanzar una excepción. |

Las excepciones son unchecked y se pueden manejar con `try/catch`. Incluí la **construcción del mensaje** dentro del bloque si querés capturar también sus validaciones.

- **NotificationValidationException:** revisar `field()` y `code()`; los datos no permiten iniciar ese intento.
- **NotificationConfigurationException:** revisar el registro, el default, las credenciales suministradas o el ID seleccionado.
- **NotificationDeliveryException:** revisar `providerId()` y `category()`. Un resultado de transporte incierto no prueba que sea seguro repetir.

## Funcionalidades opcionales

| Función | Uso y límite |
|---|---|
| [Async y lotes](examples/AsyncBatchDemo.java) | `AsyncNotificationClient.create(cliente)` administra 4 workers y 64 posiciones de espera; usar try-with-resources. `using(cliente, executor)` deja el cierre del executor externo a la aplicación. Elegí uno que ejecute cada tarea o señale el rechazo (por ejemplo, con `AbortPolicy`). Con descarte silencioso, el futuro de esa entrada puede quedar pendiente y el lote puede esperar indefinidamente. `sendAsync` devuelve un futuro; `sendBatchAsync` compone futuros por entrada, preserva posiciones y aísla fallos. `sendBatch` espera el resultado. El lote puede intentar enviar todo de una vez. |
| [Templates](examples/TemplateDemo.java) | `TextTemplates.render` sustituye variables nombradas en texto plano antes de construir el mensaje. Los valores se insertan literalmente, en una pasada. |
| [Callback](examples/CallbackDemo.java) | `ObservedNotificationClient` notifica aceptación o fallo de entrega final a un Consumer local. El callback corre en el hilo del envío; debe ser rápido y seguro para uso concurrente. No es auditoría persistente. |
| [Retry](examples/RetryDemo.java) | Registrar `RetryingSender` alrededor del sender habilita intentos inmediatos sólo ante UNAVAILABLE. El máximo incluye el primero. Incluso entonces puede haber duplicados. |

## Extender la librería

**Otro proveedor de un canal existente:** implementá `NotificationSender<EmailNotification>` (o el tipo del canal elegido), traducí los datos, validá la respuesta y registrá la instancia con un ID nuevo. No hace falta modificar el cliente.

**Otro canal:** creá un tipo que implemente `Notification`, validá sus invariantes y registrá un sender para esa clase exacta. El siguiente fragmento supone un mensaje nuevo en el código consumidor:

```java
record Aviso(String texto) implements Notification {
    Aviso {
        if (texto == null || texto.isBlank())
            throw new NotificationValidationException("texto", "REQUIRED");
    }
}
```

```java
var id = new ProviderId("local");
NotificationSender<Aviso> simulado = aviso -> new SendReceipt(id, Optional.empty(), true);
var cliente = Notifications.builder()
        .register(Aviso.class, id, simulado)
        .defaultProvider(Aviso.class, id).build();
cliente.send(new Aviso("Proceso terminado"));
```

El ejemplo usa los tipos de `dev.challenge.notifications.domain`, `configuration.Notifications`, `spi.NotificationSender` y `java.util.Optional`. El [test consumidor](src/test/java/consumer/NotificationClientTest.java) demuestra la extensión desde fuera del núcleo. Los mapas del cliente son inmutables, pero los senders se comparten: una implementación propia debe ser segura si admite envíos concurrentes.

## Seguridad, pruebas y diseño

Usá valores ficticios en las simulaciones. En un adaptador real, la aplicación debería obtener los secretos de su entorno o gestor de credenciales y pasarlos por código. No registres claves, URLs de webhook, destinatarios ni contenido. Los tipos sensibles redactan su representación de diagnóstico; eso no cifra sus valores en memoria.

`mvn clean verify` ejecuta las pruebas con JUnit 5, incluida la compilación de los ejemplos y la comprobación de dependencias entre paquetes. En Windows, [verify.ps1](scripts/verify.ps1) verifica además ejemplos contra el JAR, empaquetado y Javadoc. En el cierre verificado, [verify-docker.ps1](scripts/verify-docker.ps1) construyó la imagen Linux: pasaron 284 casos en el build y las ocho demos terminaron sin red en los contenedores.

La aplicación llama a [NotificationClient](src/main/java/dev/challenge/notifications/api/NotificationClient.java). El [cliente predeterminado](src/main/java/dev/challenge/notifications/application/DefaultNotificationClient.java) resuelve la ruta por tipo exacto y proveedor, invoca al [NotificationSender](src/main/java/dev/challenge/notifications/spi/NotificationSender.java) registrado y valida el recibo. Cada adaptador conoce el protocolo de su proveedor. Por ejemplo, [SimulatedSendGridSender](src/main/java/dev/challenge/notifications/adapters/sendgrid/SimulatedSendGridSender.java) traduce el email y comprueba el estado de aceptación.

## Uso de IA durante el challenge

Usé Codex para discutir alternativas, implementar por etapas y preparar pruebas. Primero pedí una especificación y un harness. Antes de incorporar cada opcional pedí discutir el problema, las alternativas y el costo. Dos instrucciones que guiaron el trabajo fueron «No implementar funcionalidades opcionales adicionales sin autorización» y «Antes de implementar, revisá si existe alguna contradicción con los requisitos originales y presentá una API pública mínima».

Una decisión mía fue exigir un proveedor predeterminado por canal y permitir elegir otro sólo cuando hiciera falta. Otra fue simplificar los lotes: frente a una propuesta con límites separados y una ventana de admisión, elegí delegar concurrencia, espera y rechazo al Executor de Java. Acepté el costo: la cola del pool está acotada, pero la memoria del lote crece con la cantidad de entradas. Codex ayudó a implementar esa elección y probar orden, fallos parciales y rechazo.

La IA también cometió errores: una edición automática alteró caracteres en documentación aunque los tests de Java pasaban. Esa experiencia mostró por qué las pruebas del código y la revisión del material de entrega son controles distintos.
