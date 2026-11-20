package com.tongji.llm.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Spring AI adapter for the local BGE-M3 embedding HTTP service. */
public final class LocalBgeEmbeddingModel implements EmbeddingModel {
    private final RestClient restClient;
    private final int dimensions;

    public LocalBgeEmbeddingModel(String baseUrl, int dimensions) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        if (texts == null || texts.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }

        EmbeddingBatch batch = restClient.post()
                .uri("/embeddings")
                .body(Map.of("texts", texts))
                .retrieve()
                .body(EmbeddingBatch.class);

        if (batch == null || batch.embeddings() == null || batch.embeddings().size() != texts.size()) {
            throw new IllegalStateException("Local embedding service returned an invalid batch response");
        }

        List<Embedding> results = new ArrayList<>(batch.embeddings().size());
        for (int i = 0; i < batch.embeddings().size(); i++) {
            List<Double> vector = batch.embeddings().get(i);
            if (vector == null || vector.size() != dimensions) {
                throw new IllegalStateException("Expected " + dimensions + "-dimensional local embeddings");
            }
            float[] output = new float[dimensions];
            for (int j = 0; j < dimensions; j++) {
                output[j] = vector.get(j).floatValue();
            }
            results.add(new Embedding(output, i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private record EmbeddingBatch(List<List<Double>> embeddings, int dimensions, String model) {}
}
