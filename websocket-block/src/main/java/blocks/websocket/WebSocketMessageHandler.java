package blocks.websocket;

import akka.http.javadsl.model.ws.TextMessage;
import akka.stream.javadsl.SourceQueueWithComplete;

public interface WebSocketMessageHandler {

    String generateSessionId();

    TextMessage keepAliveMessage();

    TextMessage handleException(String session, Throwable throwable, SourceQueueWithComplete<TextMessage> outgoingMessagesQueue);

    TextMessage handleTextMessage(String session, TextMessage msg, SourceQueueWithComplete<TextMessage> outgoingMessagesQueue);

    default void registerOutgoingQueue(String session, SourceQueueWithComplete<TextMessage> outgoingMessageQueue) {

    }
}
