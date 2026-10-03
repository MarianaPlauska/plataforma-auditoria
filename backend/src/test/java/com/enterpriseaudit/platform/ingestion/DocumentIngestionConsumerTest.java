package com.enterpriseaudit.platform.ingestion;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentIngestionConsumerTest {
    @Test
    void chunksTextWithOverlapAndPreservesAllCharacters() {
        String text = "a".repeat(2200);
        List<String> chunks = DocumentIngestionConsumer.chunk(text, 1800, 250);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst()).hasSize(1800);
        assertThat(chunks.get(1)).hasSize(650);
    }

    @Test
    void emptyTextProducesNoChunks() {
        assertThat(DocumentIngestionConsumer.chunk(" \n ", 1800, 250)).isEmpty();
    }
}
