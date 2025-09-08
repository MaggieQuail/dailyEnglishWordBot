package org.vpmq.dailyword;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.vpmq.dailyword.services.AiInteractionsService;
import org.vpmq.dailyword.services.DailyWordBotService;
import org.vpmq.dailyword.services.SubscriptionService;
import org.vpmq.dailyword.services.WordService;

import java.util.concurrent.Executors;

public class Main {
    public static final String GPT_KEY = "";
    public static final String BOT_TOKEN = "";
    public static final String JDBC_URL = "jdbc:sqlite:/root/c1-english-words.db";

    public static void main(String[] args) {
        // Create an OpenAI client
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(GPT_KEY).build();

        // Init DB connection config
        HikariConfig dbCfg = new HikariConfig();
        dbCfg.setJdbcUrl(JDBC_URL);

        try {
            // Init DB connection pool, init bot dependencies, create and start the bot
            HikariDataSource db = new HikariDataSource(dbCfg);
            WordService wordGetter = new WordService(db);
            SubscriptionService subscriptions = new SubscriptionService(db);
            AiInteractionsService aiInteractions = new AiInteractionsService(client);

            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new DailyWordBotService(
                aiInteractions,
                wordGetter,
                subscriptions,
                Executors.newScheduledThreadPool(1),
                BOT_TOKEN
            ));
        } catch (Exception ex) {
            ex.printStackTrace();
            // We got an exception - exit code should be different from 0
            System.exit(1);
        }
    }
}