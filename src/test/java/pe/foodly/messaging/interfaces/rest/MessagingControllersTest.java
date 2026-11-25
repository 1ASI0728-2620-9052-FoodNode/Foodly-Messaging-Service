package pe.foodly.messaging.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.foodly.messaging.application.MessagingSettings;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaReplyCommandHandler;
import pe.foodly.messaging.application.commandhandlers.SendMessageCommandHandler;
import pe.foodly.messaging.application.commands.RegisterLiaReplyCommand;
import pe.foodly.messaging.application.commands.SendMessageCommand;
import pe.foodly.messaging.application.commands.SendMessageResult;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.application.queries.GetMessagesByConversationQuery;
import pe.foodly.messaging.application.queryhandlers.GetConversationsByExplorerQueryHandler;
import pe.foodly.messaging.application.queryhandlers.GetMessagesByConversationQueryHandler;
import pe.foodly.messaging.domain.exceptions.ConversationClosedException;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;
import pe.foodly.messaging.infrastructure.security.SecurityConfig;
import pe.foodly.messaging.support.TestJwt;

/** Capa de interfaz con la cadena de seguridad real (JWT y secreto del callback) y handlers simulados. */
@WebMvcTest(
        controllers = {LiaChatController.class, ConversationController.class, N8nCallbackController.class},
        properties = {
            "messaging.security.jwt.secret-base64=" + TestJwt.SECRET_BASE64,
            "messaging.security.callback.secret=" + TestJwt.CALLBACK_SECRET
        })
@Import(SecurityConfig.class)
class MessagingControllersTest {

    private static final String EXPLORER = "11111111-1111-1111-1111-111111111111";
    private static final String CONVERSATION = "5b0d3e8a-6f43-4a0e-9d38-2d7b1c4f9a10";
    private static final String MESSAGE = "0f6b8c52-1d3a-4c7e-8a55-9b1e2f3a4d60";

    @TestConfiguration
    static class Beans {
        @Bean
        MessagingSettings messagingSettings() {
            return new MessagingSettings(1000, 2);
        }

        /** Ejecuta en el mismo hilo para poder verificar al handler dentro de la prueba. */
        @Bean(name = "liaCallbackExecutor")
        Executor liaCallbackExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private SendMessageCommandHandler sendMessage;
    @MockitoBean
    private GetConversationsByExplorerQueryHandler getConversations;
    @MockitoBean
    private GetMessagesByConversationQueryHandler getMessages;
    @MockitoBean
    private RegisterLiaReplyCommandHandler registerLiaReply;

    private static String explorerToken() {
        return "Bearer " + TestJwt.explorer(EXPLORER);
    }

    private static MockHttpServletRequestBuilder chat(String body) {
        return post("/messaging/chat").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // ---- POST /messaging/chat ----

    @Test
    void chatResponde202ConElIdDeLaConversacionYDelMensaje() throws Exception {
        when(sendMessage.handle(any())).thenReturn(
                new SendMessageResult(ConversationId.of(CONVERSATION), MessageId.of(MESSAGE)));
        String token = TestJwt.explorer(EXPLORER);

        mvc.perform(chat("""
                        { "content": "Quiero un ceviche barato cerca", "latitude": -12.1219, "longitude": -77.0297 }""")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION))
                .andExpect(jsonPath("$.messageId").value(MESSAGE))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        ArgumentCaptor<SendMessageCommand> captor = ArgumentCaptor.forClass(SendMessageCommand.class);
        verify(sendMessage).handle(captor.capture());
        SendMessageCommand command = captor.getValue();
        assertThat(command.explorerId()).isEqualTo(ExplorerId.of(EXPLORER));
        assertThat(command.conversationId()).isNull();
        assertThat(command.content().text()).isEqualTo("Quiero un ceviche barato cerca");
        assertThat(command.location().latitude()).isEqualTo(-12.1219);
        assertThat(command.location().longitude()).isEqualTo(-77.0297);
        // El token crudo viaja en el comando para el ExplorerTokenCache.
        assertThat(command.token().value()).isEqualTo(token);
        assertThat(command.token().expiresAt()).isAfter(Instant.now());
    }

    @Test
    void chatConConversacionExplicitaYSinUbicacionEsValido() throws Exception {
        when(sendMessage.handle(any())).thenReturn(
                new SendMessageResult(ConversationId.of(CONVERSATION), MessageId.of(MESSAGE)));

        mvc.perform(chat("{ \"content\": \"hola\", \"conversationId\": \"" + CONVERSATION + "\" }")
                        .header("Authorization", explorerToken()))
                .andExpect(status().isAccepted());

        ArgumentCaptor<SendMessageCommand> captor = ArgumentCaptor.forClass(SendMessageCommand.class);
        verify(sendMessage).handle(captor.capture());
        assertThat(captor.getValue().conversationId()).isEqualTo(ConversationId.of(CONVERSATION));
        assertThat(captor.getValue().location()).isNull();
    }

    @Test
    void chatConMensajeVacioDa400() throws Exception {
        for (String body : List.of("{ \"content\": \"\" }", "{ \"content\": \"   \" }", "{}")) {
            mvc.perform(chat(body).header("Authorization", explorerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no puede estar vacío")))
                    .andExpect(jsonPath("$.path").value("/messaging/chat"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
        verifyNoInteractions(sendMessage);
    }

    @Test
    void chatConMensajeMasLargoQueElMaximoDa400() throws Exception {
        mvc.perform(chat("{ \"content\": \"" + "a".repeat(1001) + "\" }").header("Authorization", explorerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1000 caracteres")));
        verifyNoInteractions(sendMessage);
    }

    @Test
    void chatConCoordenadasInvalidasDa400() throws Exception {
        for (String body : List.of(
                "{ \"content\": \"hola\", \"latitude\": 91, \"longitude\": 0 }",
                "{ \"content\": \"hola\", \"latitude\": 0, \"longitude\": -181 }")) {
            mvc.perform(chat(body).header("Authorization", explorerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.matchesPattern(".*(latitud|longitud).*")));
        }
        verifyNoInteractions(sendMessage);
    }

    @Test
    void chatConSoloUnaCoordenadaDa400() throws Exception {
        mvc.perform(chat("{ \"content\": \"hola\", \"latitude\": -12.1 }").header("Authorization", explorerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("deben enviarse juntas")));
        verifyNoInteractions(sendMessage);
    }

    @Test
    void chatConJsonMalFormadoOIdInvalidoDa400SinFiltrarDetallesInternos() throws Exception {
        for (String body : List.of("{ esto no es json", "{ \"content\": \"hola\", \"conversationId\": \"abc\" }")) {
            mvc.perform(chat(body).header("Authorization", explorerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("Jackson"))));
        }
    }

    @Test
    void chatSinTokenDa401ConElFormatoUniforme() throws Exception {
        mvc.perform(chat("{ \"content\": \"hola\" }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/messaging/chat"));
        verifyNoInteractions(sendMessage);
    }

    @Test
    void chatConTokenInvalidoDa401() throws Exception {
        String forged = TestJwt.token(
                TestJwt.OTHER_SECRET_BASE64, EXPLORER, TestJwt.EXPLORER_ROLE,
                Instant.now().plusSeconds(3600));
        String expired = TestJwt.token(TestJwt.SECRET_BASE64, EXPLORER, TestJwt.EXPLORER_ROLE, Instant.now().minusSeconds(60));
        String withoutExpiration = TestJwt.token(TestJwt.SECRET_BASE64, EXPLORER, TestJwt.EXPLORER_ROLE, null);
        String notAUuid = TestJwt.token(TestJwt.SECRET_BASE64, "no-es-uuid", TestJwt.EXPLORER_ROLE, Instant.now().plusSeconds(60));

        // iss y aud: el contrato JWT v1 de IAM exige iss=foodly-iam y que aud incluya foodly-messaging
        String otherIssuer = TestJwt.withIssuer(EXPLORER, "otro-emisor");
        String noIssuer = TestJwt.withIssuer(EXPLORER, null);
        String noAudience = TestJwt.withAudience(EXPLORER, null);
        String notForMessaging = TestJwt.withAudience(EXPLORER, List.of("foodly-iam", "foodly-business"));
        String base64TextAsKey = TestJwt.signedWithTheBase64TextAsKey(EXPLORER);

        for (String token : List.of(forged, expired, withoutExpiration, notAUuid, otherIssuer, noIssuer, noAudience,
                notForMessaging, base64TextAsKey, "basura")) {
            mvc.perform(chat("{ \"content\": \"hola\" }").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(sendMessage);
    }

    @Test
    void chatConRolHuariqueOwnerDa403() throws Exception {
        mvc.perform(chat("{ \"content\": \"hola\" }")
                        .header("Authorization", "Bearer " + TestJwt.owner(EXPLORER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("explorador gastronómico")));
        verifyNoInteractions(sendMessage);
    }

    @Test
    void chatAceptaElRolComoListaYConPrefijoRole() throws Exception {
        when(sendMessage.handle(any())).thenReturn(
                new SendMessageResult(ConversationId.of(CONVERSATION), MessageId.of(MESSAGE)));
        String token = TestJwt.token(TestJwt.SECRET_BASE64, EXPLORER, List.of("ROLE_gastronomic_explorer"),
                Instant.now().plusSeconds(3600));

        mvc.perform(chat("{ \"content\": \"hola\" }").header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());
    }

    @Test
    void chatConConversacionAjenaOInexistenteDa404() throws Exception {
        when(sendMessage.handle(any())).thenThrow(new ConversationNotFoundException(ConversationId.of(CONVERSATION)));

        mvc.perform(chat("{ \"content\": \"hola\", \"conversationId\": \"" + CONVERSATION + "\" }")
                        .header("Authorization", explorerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("La conversación no existe o no te pertenece."));
    }

    @Test
    void chatConConversacionCerradaDa409() throws Exception {
        when(sendMessage.handle(any())).thenThrow(new ConversationClosedException(ConversationId.of(CONVERSATION)));

        mvc.perform(chat("{ \"content\": \"hola\", \"conversationId\": \"" + CONVERSATION + "\" }")
                        .header("Authorization", explorerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---- GET /messaging/conversations/** ----

    @Test
    void misConversacionesResponde200ConLaLista() throws Exception {
        Conversation conversation = Conversation.start(ExplorerId.of(EXPLORER));
        when(getConversations.handle(any())).thenReturn(List.of(conversation));

        mvc.perform(get("/messaging/conversations/me").header("Authorization", explorerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(conversation.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].startedAt").exists())
                .andExpect(jsonPath("$[0].lastMessageAt").exists());
    }

    @Test
    void misConversacionesSinTokenDa401YConOwnerDa403() throws Exception {
        mvc.perform(get("/messaging/conversations/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/messaging/conversations/me").header("Authorization", "Bearer " + TestJwt.owner(EXPLORER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void losMensajesSeDevuelvenConIntencionHuariqueYReserva() throws Exception {
        Conversation conversation = Conversation.start(ExplorerId.of(EXPLORER));
        Message question = conversation.addExplorerMessage(MessageContent.of("reserva para 4 mañana 8 pm"));
        HuariqueId huarique = HuariqueId.of("a1111111-1111-4111-8111-111111111111");
        UserIntent intent = new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, 4,
                LocalDateTime.of(2026, 11, 14, 20, 0), huarique);
        Message reply = conversation.addLiaReply(MessageContent.of("Listo"), intent, question.getId());
        ReservationId reservation = ReservationId.of("33333333-3333-3333-3333-333333333333");
        conversation.linkReservation(question.getId(), reservation);
        when(getMessages.handle(any())).thenReturn(conversation.getMessages());

        mvc.perform(get("/messaging/conversations/" + CONVERSATION + "/messages")
                        .header("Authorization", explorerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(question.getId().toString()))
                .andExpect(jsonPath("$[0].sender").value("EXPLORER"))
                .andExpect(jsonPath("$[0].content").value("reserva para 4 mañana 8 pm"))
                .andExpect(jsonPath("$[0].intentType").doesNotExist())
                .andExpect(jsonPath("$[0].reservationId").value(reservation.toString()))
                .andExpect(jsonPath("$[1].id").value(reply.getId().toString()))
                .andExpect(jsonPath("$[1].sender").value("LIA"))
                .andExpect(jsonPath("$[1].intentType").value("RESERVATION_REQUEST"))
                .andExpect(jsonPath("$[1].huariqueId").value(huarique.toString()))
                .andExpect(jsonPath("$[1].sentAt").exists());

        ArgumentCaptor<GetMessagesByConversationQuery> captor = ArgumentCaptor.forClass(GetMessagesByConversationQuery.class);
        verify(getMessages).handle(captor.capture());
        assertThat(captor.getValue().conversationId()).isEqualTo(ConversationId.of(CONVERSATION));
        assertThat(captor.getValue().explorerId()).isEqualTo(ExplorerId.of(EXPLORER));
    }

    @Test
    void losMensajesDeUnaConversacionAjenaDan404() throws Exception {
        when(getMessages.handle(any())).thenThrow(new ConversationNotFoundException(ConversationId.of(CONVERSATION)));

        mvc.perform(get("/messaging/conversations/" + CONVERSATION + "/messages")
                        .header("Authorization", explorerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/messaging/conversations/" + CONVERSATION + "/messages"));
    }

    @Test
    void losMensajesConUnIdInvalidoDan400() throws Exception {
        mvc.perform(get("/messaging/conversations/no-es-un-uuid/messages").header("Authorization", explorerToken()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(getMessages);
    }

    // ---- POST /messaging/callbacks/n8n ----

    private static final String CALLBACK_BODY = """
            { "conversationId": "5b0d3e8a-6f43-4a0e-9d38-2d7b1c4f9a10",
              "inReplyToMessageId": "0f6b8c52-1d3a-4c7e-8a55-9b1e2f3a4d60",
              "replyText": "¡Claro! Te busco opciones cerca…",
              "intent": { "type": "RECOMMENDATION_REQUEST", "cravings": "ceviche",
                          "budgetRange": { "min": 0, "max": 25 },
                          "location": { "latitude": -12.12, "longitude": -77.03 },
                          "partySize": null, "requestedAt": null, "huariqueId": null, "missingContext": [] },
              "suggestedHuariques": [], "requestedReservation": null }""";

    private static MockHttpServletRequestBuilder callback(String body) {
        return post("/messaging/callbacks/n8n").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void elCallbackValidoSeAceptaConRecibidoYSeConvierteEnElCommand() throws Exception {
        mvc.perform(callback(CALLBACK_BODY).header("X-Callback-Secret", TestJwt.CALLBACK_SECRET))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        ArgumentCaptor<RegisterLiaReplyCommand> captor = ArgumentCaptor.forClass(RegisterLiaReplyCommand.class);
        verify(registerLiaReply).handle(captor.capture());
        RegisterLiaReplyCommand command = captor.getValue();
        assertThat(command.conversationId()).isEqualTo(ConversationId.of(CONVERSATION));
        assertThat(command.inReplyToMessageId()).isEqualTo(MessageId.of(MESSAGE));
        assertThat(command.replyContent()).isEqualTo("¡Claro! Te busco opciones cerca…");
        assertThat(command.intent().type()).isEqualTo(IntentType.RECOMMENDATION_REQUEST);
        assertThat(command.intent().cravings()).isEqualTo("ceviche");
        assertThat(command.intent().budgetRange().max()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(command.intent().location().latitude()).isEqualTo(-12.12);
        assertThat(command.suggestedHuariques()).isEmpty();
        assertThat(command.requestedReservation()).isNull();
    }

    @Test
    void elCallbackConSecretoIncorrectoDa401YNoProcesaNada() throws Exception {
        mvc.perform(callback(CALLBACK_BODY).header("X-Callback-Secret", "secreto-equivocado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/messaging/callbacks/n8n"));
        verifyNoInteractions(registerLiaReply);
    }

    @Test
    void elCallbackSinSecretoDa401AunqueLlevePorUnJwtDeExplorador() throws Exception {
        mvc.perform(callback(CALLBACK_BODY)).andExpect(status().isUnauthorized());
        mvc.perform(callback(CALLBACK_BODY).header("Authorization", explorerToken()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registerLiaReply);
    }

    @Test
    void elSecretoDelCallbackNoSirveEnLosEndpointsDeUsuario() throws Exception {
        mvc.perform(chat("{ \"content\": \"hola\" }").header("X-Callback-Secret", TestJwt.CALLBACK_SECRET))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elCallbackConCuerpoInvalidoDa400() throws Exception {
        String withoutReplyTo = CALLBACK_BODY.replace("\"inReplyToMessageId\": \"" + MESSAGE + "\",", "");
        String unknownType = CALLBACK_BODY.replace("RECOMMENDATION_REQUEST", "ADIVINAR");
        String invertedBudget = CALLBACK_BODY.replace("\"min\": 0, \"max\": 25", "\"min\": 30, \"max\": 25");
        String invalidLatitude = CALLBACK_BODY.replace("\"latitude\": -12.12", "\"latitude\": -120.12");

        for (String body : List.of(withoutReplyTo, unknownType, invertedBudget, invalidLatitude, "{ no es json")) {
            mvc.perform(callback(body).header("X-Callback-Secret", TestJwt.CALLBACK_SECRET))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(registerLiaReply);
    }

    @Test
    void elCallbackAceptaLaReservaSolicitadaYLosHuariquesSugeridosEnCualquierFormato() throws Exception {
        String body = """
                { "conversationId": "%s", "inReplyToMessageId": "%s", "replyText": "Listo",
                  "intent": { "type": "RESERVATION_REQUEST", "partySize": 4 },
                  "suggestedHuariques": [ "a1111111-1111-4111-8111-111111111111",
                                          { "huariqueId": "a2222222-2222-4222-8222-222222222222", "name": "X" },
                                          { "algo": "raro" }, 42 ],
                  "requestedReservation": { "huariqueId": "a1111111-1111-4111-8111-111111111111",
                                            "requestedAt": "2026-11-20T20:00:00", "partySize": 4 } }"""
                .formatted(CONVERSATION, MESSAGE);

        mvc.perform(callback(body).header("X-Callback-Secret", TestJwt.CALLBACK_SECRET))
                .andExpect(status().isAccepted());

        ArgumentCaptor<RegisterLiaReplyCommand> captor = ArgumentCaptor.forClass(RegisterLiaReplyCommand.class);
        verify(registerLiaReply).handle(captor.capture());
        assertThat(captor.getValue().suggestedHuariques()).extracting(HuariqueId::toString)
                .containsExactly("a1111111-1111-4111-8111-111111111111", "a2222222-2222-4222-8222-222222222222");
        assertThat(captor.getValue().requestedReservation().requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 20, 0));
        assertThat(captor.getValue().requestedReservation().partySize()).isEqualTo(4);
    }

    @Test
    void siElProcesamientoFallaElCallbackYaRespondio202() throws Exception {
        doThrow(new ConversationNotFoundException(ConversationId.of(CONVERSATION)))
                .when(registerLiaReply).handle(any());

        mvc.perform(callback(CALLBACK_BODY).header("X-Callback-Secret", TestJwt.CALLBACK_SECRET))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    // ---- rutas públicas y desconocidas ----

    @Test
    void lasRutasPublicasNoPidenTokenYLasDesconocidasSi() throws Exception {
        for (String path : List.of("/actuator/health", "/swagger-ui.html", "/v3/api-docs")) {
            int code = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(code).as(path).isNotIn(401, 403);
        }
        mvc.perform(get("/messaging/otra-cosa")).andExpect(status().isUnauthorized());
    }
}
