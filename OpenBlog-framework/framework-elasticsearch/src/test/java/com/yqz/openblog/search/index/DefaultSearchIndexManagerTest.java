package com.yqz.openblog.search.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSearchIndexManagerTest {

    private static final SearchIndex INDEX = new SimpleSearchIndex("test_index", Doc.class);
    private static final Document MAPPING =
            Document.parse("{\"properties\":{\"title\":{\"type\":\"text\"}}}");

    @Mock
    private ElasticsearchOperations esOps;

    @Mock
    private IndexOperations indexOperations;

    private DefaultSearchIndexManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultSearchIndexManager(esOps);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsMissingIndexWithMapping() {
        when(esOps.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);
        when(indexOperations.createMapping(any(Class.class))).thenReturn(MAPPING);

        assertThat(manager.ensureIndex(INDEX)).isTrue();

        verify(indexOperations).create();
        verify(indexOperations).putMapping(MAPPING);
    }

    @Test
    @SuppressWarnings("unchecked")
    void existingIndexOnlyUpdatesMapping() {
        when(esOps.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
        when(indexOperations.createMapping(any(Class.class))).thenReturn(MAPPING);

        assertThat(manager.ensureIndex(INDEX)).isTrue();

        verify(indexOperations, never()).create();
        verify(indexOperations).putMapping(MAPPING);
    }

    @Test
    void returnsFalseWhenEsIsUnavailable() {
        when(esOps.indexOps(any(IndexCoordinates.class)))
                .thenThrow(new RuntimeException("es down"));

        assertThat(manager.ensureIndex(INDEX)).isFalse();
        assertThat(manager.indexExists(INDEX)).isFalse();
    }

    record Doc(String title) {
    }
}
