package com.example.supportbot;

import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/** A tiny customer-support bot: the LLM feature under test. */
public class SupportBot {

    static final String SYSTEM_PROMPT = """
            You answer questions for an online shop. Refunds are possible within 30 days of delivery, \
            to the original payment method only. Answer in two sentences.""";

    private final ChatModel model;

    public SupportBot(ChatModel model) {
        this.model = model;
    }

    public String answer(String question) {
        Prompt prompt = new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(question)));
        return model.call(prompt).getResult().getOutput().getText();
    }
}
