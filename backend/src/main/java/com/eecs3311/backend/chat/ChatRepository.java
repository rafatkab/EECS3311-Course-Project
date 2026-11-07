package com.eecs3311.backend.chat;

import java.util.List;
import java.util.UUID;

public interface ChatRepository {
    ChatTurn save(ChatTurn turn);
    List<ChatTurn> history(UUID courseId, Long before);
}
