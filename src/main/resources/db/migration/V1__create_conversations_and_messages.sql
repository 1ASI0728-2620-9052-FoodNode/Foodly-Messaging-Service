-- Messaging Service: diagrama de base de datos 5.6.7.2.
-- Sin FK física en explorer_id (IAM), huarique_id (Business) ni reservation_id (Reservation):
-- cada Bounded Context es dueño de su base de datos.
-- Las fechas son DATETIME(6) (microsegundos) para que el historial conserve el orden cuando dos
-- mensajes caen en el mismo segundo. Ver DEVIATIONS.md.

CREATE TABLE conversations (
    id              CHAR(36)    NOT NULL,
    explorer_id     CHAR(36)    NOT NULL,
    status          VARCHAR(10) NOT NULL,
    started_at      DATETIME(6) NOT NULL,
    last_message_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_conversations_explorer_status ON conversations (explorer_id, status);

CREATE TABLE messages (
    id              CHAR(36)    NOT NULL,
    conversation_id CHAR(36)    NOT NULL,
    sender          VARCHAR(10) NOT NULL,
    content         TEXT        NOT NULL,
    intent_type     VARCHAR(30) NULL,
    intent_data     JSON        NULL,
    huarique_id     CHAR(36)    NULL,
    reservation_id  CHAR(36)    NULL,
    sent_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);

CREATE INDEX idx_messages_conversation_sent_at ON messages (conversation_id, sent_at);
