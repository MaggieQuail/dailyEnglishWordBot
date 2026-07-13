package org.vpmq.dailyword.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberBanned;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberLeft;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.vpmq.dailyword.models.StateModel;
import org.vpmq.dailyword.models.SubscriptionModel;
import org.vpmq.dailyword.models.VocabularyModel;
import org.vpmq.dailyword.utils.MarkdownEscaper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DailyWordBotService extends TelegramLongPollingBot {

    private static final Logger LOGGER = LogManager.getLogger(DailyWordBotService.class);

    private final AiInteractionsService aiInteractions;
    private final SubscriptionService subscriptions;
    private final WordService wordGetter;
    private final StateService states;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();

    private VocabularyModel wordOfTheDay;
    private byte[] wordSound;
    private StateModel state;

    public DailyWordBotService(AiInteractionsService aiInteractions,
                               WordService wordGetter,
                               SubscriptionService subscriptions,
                               StateService states,
                               ScheduledExecutorService scheduler,
                               String botToken) {
        super(botToken);
        this.aiInteractions = aiInteractions;
        this.subscriptions = subscriptions;
        this.wordGetter = wordGetter;
        this.states = states;

        this.state = this.states.getDefault();

        this.wordOfTheDay = wordGetter.getById(this.state.getWordId());
        this.wordOfTheDay.explanation = aiInteractions.wordUsageExamples(wordOfTheDay.word);
        this.wordSound = aiInteractions.textToSpeech(wordOfTheDay.word);
        this.updateWordOfTheDayPeriodically(scheduler);
    }

    @Override
    public String getBotUsername() {
        return "SuperPuperDuperAIBot";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            // Got a button push - handle it
            handleCallback(update.getCallbackQuery());
        }

        Message message = update.getMessage();
        if (message != null && "/start".equalsIgnoreCase(message.getText())) {
            // Got a message from a new user - send him/her the word of the day
            // and subscribe for receiving new words daily
            long userId = message.getFrom().getId();

            // Subscribe a new user
            subscribeNewUser(userId, message.getChatId());

            // Send a "word of the day" message
            InlineKeyboardMarkup markup = buttonCreation(true, true, true);
            Optional<Message> sentMessage = sendText(userId, generateMessageText(false, false), markup);
            if (sentMessage.isPresent()) {
                // Save message ID in subscriptions table
                subscriptions.updateLastMessage(userId, sentMessage.get().getMessageId());
            }
        }

        if (update.hasMyChatMember()) {
            // Got a chat membership update - handle it
            ChatMemberUpdated chatMemberUpdate = update.getMyChatMember();
            // leave and block -> unsubscribe
            String memberStatus = chatMemberUpdate.getNewChatMember().getStatus();
            if (ChatMemberBanned.STATUS.equalsIgnoreCase(memberStatus)
                || ChatMemberLeft.STATUS.equalsIgnoreCase(memberStatus)
            ) {
                unsubscribeUser(chatMemberUpdate.getFrom().getId());
            }
        }
    }

    // Response to client
    public Optional<Message> sendText(Long who, String text, InlineKeyboardMarkup keyboard) {
        SendMessage sm = SendMessage.builder()
            .chatId(who.toString())
            .replyMarkup(keyboard)
            .parseMode("MarkdownV2")
            .text(MarkdownEscaper.escape(text)).build();
        try {
            return Optional.ofNullable(execute(sm));
        } catch (TelegramApiException e) {
            LOGGER.error("Failed to send message to user {}", who, e);
        }
        return Optional.empty();
    }

    public InlineKeyboardMarkup buttonCreation(boolean translateNeeded, boolean explainNeeded, boolean soundNeeded) {
        var translateButton = InlineKeyboardButton.builder()
            .text("Translate").callbackData("translate")
            .build();
        var explainButton = InlineKeyboardButton.builder()
            .text("Explain").callbackData("explain")
            .build();
        var soundButton = InlineKeyboardButton.builder()
            .text("Sound").callbackData("sound")
            .build();
        List<InlineKeyboardButton> listOfButtons = new ArrayList<>();

        if (translateNeeded) {
            listOfButtons.add(translateButton);
        }
        if (explainNeeded) {
            listOfButtons.add(explainButton);
        }
        if (soundNeeded) {
            listOfButtons.add(soundButton);
        }
        return InlineKeyboardMarkup.builder()
            .keyboardRow(listOfButtons).build();
    }

    private String getClickedButtonName(CallbackQuery query) {
        return query.getData();
    }

    private InlineKeyboardMarkup hideButton(List<List<InlineKeyboardButton>> kb, String buttonName) {
        List<List<InlineKeyboardButton>> newKb = new ArrayList<>();
        for (var row : kb) {
            List<InlineKeyboardButton> newRow = new ArrayList<>();
            for (var button : row) {
                if (Objects.equals(buttonName, button.getCallbackData())) {
                    continue;
                }
                newRow.add(button);
            }
            newKb.add(newRow);
        }
        return InlineKeyboardMarkup.builder().keyboard(newKb).build();
    }

    private void subscribeNewUser(long userId, long chatId) {
        subscriptions.addSubscription(userId, chatId);
    }

    private void unsubscribeUser(Long userId) {
        subscriptions.deleteSubscription(userId);
    }

    private void handleCallback(CallbackQuery callback) {
        Message targetMessage = callback.getMessage();

        // Get clicked button name
        String buttonName = getClickedButtonName(callback);

        // Get keyboard from the previous message
        List<List<InlineKeyboardButton>> kb = targetMessage.getReplyMarkup().getKeyboard();

        // Get existing buttons from the keyboard
        Set<String> existingButtons = getButtonNames(kb);

        // Remove clicked button
        existingButtons.remove(buttonName);

        // Missing button == clicked button
        boolean withTranslation = !existingButtons.contains("translate");
        boolean withExamples = !existingButtons.contains("explain");

        // Generate new text for the message
        String newText = generateMessageText(withTranslation, withExamples);

        // Hide clicked button
        InlineKeyboardMarkup newKb = hideButton(kb, buttonName);

        switch (buttonName) {
            case "translate", "explain" -> {
                editMessage(targetMessage.getChatId(), targetMessage.getMessageId(), newText, newKb);
            }
            case "sound" -> {
                // Hide clicked buttons
                editMessageMarkup(targetMessage, newKb);
                // Send a word sound message
                Optional<Message> soundMsg = sendWordSound(callback.getMessage().getChatId());
                if (soundMsg.isPresent()) {
                    // Save word sound message ID in the subscription
                    subscriptions.updateLastSound(callback.getFrom().getId(), soundMsg.get().getMessageId());
                }
            }
        }
    }

    private void editMessage(long chatId,
                             int messageId,
                             String newText,
                             InlineKeyboardMarkup newKb) {

        EditMessageText em = EditMessageText.builder()
            .chatId(chatId)
            .messageId(messageId)
            .text(MarkdownEscaper.escape(newText))
            .replyMarkup(newKb)
            .parseMode("MarkdownV2")
            .build();
        try {
            execute(em);
        } catch (TelegramApiException e) {
            if (e.getMessage().contains("message is not modified")) {
                return;
            }
            LOGGER.error("Failed to edit message {} in chat {}", messageId, chatId, e);
        }
    }

    private void editMessageMarkup(Message message, InlineKeyboardMarkup newKb) {
        EditMessageReplyMarkup em = EditMessageReplyMarkup.builder()
            .chatId(message.getChatId())
            .messageId(message.getMessageId())
            .replyMarkup(newKb)
            .build();
        try {
            execute(em);
        } catch (TelegramApiException e) {
            LOGGER.error(
                "Failed to edit markup of message {} in chat {}",
                message.getMessageId(), message.getChatId(), e
            );
        }
    }

    private Optional<Message> sendWordSound(Long chatId) {
        try {
            InputFile audioFile = new InputFile(
                new ByteArrayInputStream(this.wordSound),
                this.wordOfTheDay.word + ".mp3"
            );
            SendAudio sendAudio = new SendAudio();
            sendAudio.setChatId(String.valueOf(chatId));
            sendAudio.setAudio(audioFile);

            return Optional.ofNullable(execute(sendAudio));
        } catch (Exception ex) {
            LOGGER.error("Failed to send word sound message to chat {}", chatId, ex);
        }
        return Optional.empty();
    }

    private void updateWordOfTheDayPeriodically(ScheduledExecutorService scheduler) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC+3"));
        ZonedDateTime nextRun = now.toLocalDate().atTime(9, 0).atZone(ZoneId.of("UTC+3"));
        if (!now.isBefore(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        long initialDelay = nextRun.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();
        LOGGER.info("First word update scheduled in {} minutes", TimeUnit.MILLISECONDS.toMinutes(initialDelay));

        scheduler.scheduleAtFixedRate(() -> {
            LOGGER.info("Running word update");
            writeLock.lock();
            try {
                // List active subscriptions
                List<SubscriptionModel> activeSubscriptions = subscriptions.getSubscriptions();
                // Drop sound messages
                dropSoundMessages(activeSubscriptions);
                // Finalize old messages
                finalizeOldMessages(activeSubscriptions);

                // Get ID of the next word of the day
                int nextId = wordOfTheDay.id + 1;
                if (nextId > wordGetter.getWordCount()) {
                    // We went through all the words - start again from the beginning
                    nextId = 1;
                }
                // Replace word of the day with the next one
                wordOfTheDay = wordGetter.getById(nextId);
                wordOfTheDay.explanation = aiInteractions.wordUsageExamples(wordOfTheDay.word);
                wordSound = aiInteractions.textToSpeech(wordOfTheDay.word);

                // Save current word ID to continue the word sequence after failure or reboot
                state.setWordId(wordOfTheDay.id);
                this.states.update(state);

                // Send new word to all subscribed users
                LOGGER.info("Broadcasting word {}", wordOfTheDay.id);
                broadcastNewWord(activeSubscriptions);
            } catch (Exception ex) {
                LOGGER.error("Daily word update failed", ex);
            } finally {
                writeLock.unlock();
            }
        }, initialDelay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
    }

    private void broadcastNewWord(List<SubscriptionModel> activeSubscriptions) {
        InlineKeyboardMarkup markup = buttonCreation(true, true, true);
        String text = generateMessageText(false, false);
        activeSubscriptions.forEach(sub -> {
            Optional<Message> sentMessage = sendText(sub.getUserId(), text, markup);
            if (sentMessage.isPresent()) {
                subscriptions.updateLastMessage(sub.getUserId(), sentMessage.get().getMessageId());
            }
        });
    }

    private void dropSoundMessages(List<SubscriptionModel> activeSubscriptions) {
        activeSubscriptions.forEach(sub -> {
            int lastSoundId = sub.getLastSoundId();
            if (lastSoundId == 0) {
                // User didn't ask for a word sound message -> skip deletion
                return;
            }
            DeleteMessage delete = DeleteMessage.builder()
                .chatId(sub.getChatId())
                .messageId(lastSoundId)
                .build();
            try {
                execute(delete);
                subscriptions.updateLastSound(sub.getUserId(), null);
            } catch (Exception ex) {
                LOGGER.error("Failed to drop sound message {} in chat {}", lastSoundId, sub.getChatId(), ex);
            }
        });
    }

    private void finalizeOldMessages(List<SubscriptionModel> activeSubscriptions) {
        // Generate a full text of the "word of the day" message
        String fullText = generateMessageText(true, true);
        // Create an empty keyboard
        InlineKeyboardMarkup noButtonsKb = InlineKeyboardMarkup.builder().build();
        // Go over subscriptions and finalize old messages - update text and remove buttons
        // for each of them
        activeSubscriptions.forEach(sub -> {
            if (sub.getLastMsgId() == 0) {
                return;
            }
            editMessage(sub.getChatId(), sub.getLastMsgId(), fullText, noButtonsKb);
        });
    }

    private String generateMessageText(boolean withTranslation, boolean withExamples) {
        // Message should always contain the word of the day
        String text = "\uD83D\uDFE9 `" + wordOfTheDay.word + "`";
        // Add translation if necessary
        if (withTranslation) {
            text += " - " + wordOfTheDay.translation;
        }
        // Generate examples if necessary
        if (withExamples) {
            text += "\n\n" + this.wordOfTheDay.explanation;
        }
        return text;
    }

    private Set<String> getButtonNames(List<List<InlineKeyboardButton>> kb) {
        Set<String> names = new HashSet<>();
        kb.forEach(row -> row.forEach(button -> names.add(button.getCallbackData())));
        return names;
    }

}
