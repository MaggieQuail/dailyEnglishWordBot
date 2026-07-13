package org.vpmq.dailyword.services;

import com.openai.client.OpenAIClient;
import com.openai.core.http.HttpResponse;
import com.openai.models.ChatModel;
import com.openai.models.audio.speech.SpeechCreateParams;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class AiInteractionsService {

    private static final String USAGE_EXAMPLES_PROMPT_PATH = "word-usage-examples-prompt.txt";

    private final OpenAIClient client;
    private final String prompt;

    public AiInteractionsService(OpenAIClient client) throws IOException {
        this.client = client;
        InputStream stream = this.getClass().getResourceAsStream(USAGE_EXAMPLES_PROMPT_PATH);
        if (stream == null) {
            throw new IOException("Resource '%s' is missing".formatted(USAGE_EXAMPLES_PROMPT_PATH));
        }
        try (stream) {
            this.prompt = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String wordUsageExamples(String word) {
        ResponseCreateParams params = ResponseCreateParams.builder()
            .input(this.prompt + word)
            .model(ChatModel.GPT_4_1_NANO)
            .temperature(0.3)
            .build();
        Response response = client.responses().create(params);

        return response.output().stream()
            .flatMap(item -> item.message().stream())
            .flatMap(mes -> mes.content().stream())
            .flatMap(content -> content.outputText().stream())
            .map(ResponseOutputText::text)
            .collect(Collectors.joining());
    }

    public byte[] textToSpeech(String text) {
        SpeechCreateParams speechCreateParams = SpeechCreateParams.builder()
            .input(text)
            .voice("ash")
            .model("gpt-4o-mini-tts")
            .responseFormat(SpeechCreateParams.ResponseFormat.MP3)
            .build();
        HttpResponse response = client.audio().speech().create(speechCreateParams);
        try (InputStream stream = response.body()) {
            return stream.readAllBytes();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
