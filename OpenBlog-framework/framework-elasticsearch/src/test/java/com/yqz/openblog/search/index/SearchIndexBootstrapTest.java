package com.yqz.openblog.search.index;

import com.yqz.openblog.search.config.SearchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchIndexBootstrapTest {

    private static final SearchIndex ARTICLES = new SimpleSearchIndex("openblog_articles", Doc.class);
    private static final SearchIndex PROJECTS = new SimpleSearchIndex("openblog_projects", Doc.class);

    @Test
    void ensuresEveryIndexOnStartup() {
        SearchIndexManager manager = mock(SearchIndexManager.class);
        when(manager.ensureIndex(ARTICLES)).thenReturn(true);
        when(manager.ensureIndex(PROJECTS)).thenReturn(true);

        bootstrap(manager, new SearchProperties(), ARTICLES, PROJECTS).onApplicationReady();

        verify(manager, times(2)).ensureIndex(org.mockito.ArgumentMatchers.any(SearchIndex.class));
    }

    @Test
    void skipsWhenAutoCreateDisabled() {
        SearchIndexManager manager = mock(SearchIndexManager.class);
        SearchProperties properties = new SearchProperties();
        properties.setAutoCreateIndex(false);

        bootstrap(manager, properties, ARTICLES).onApplicationReady();

        verifyNoInteractions(manager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void managerFailureDoesNotBreakStartup() {
        SearchIndexManager manager = mock(SearchIndexManager.class);
        when(manager.ensureIndex(ARTICLES)).thenThrow(new RuntimeException("es down"));

        assertThatCode(() -> bootstrap(manager, new SearchProperties(), ARTICLES).onApplicationReady())
                .doesNotThrowAnyException();
    }

    @Test
    void disabledIndexesAreNotTouched() {
        SearchIndexManager manager = mock(SearchIndexManager.class);

        bootstrap(manager, new SearchProperties()).onApplicationReady();

        verify(manager, never()).ensureIndex(org.mockito.ArgumentMatchers.any());
    }

    @SuppressWarnings("unchecked")
    private static SearchIndexBootstrap bootstrap(SearchIndexManager manager,
                                                  SearchProperties properties,
                                                  SearchIndex... indexes) {
        ObjectProvider<SearchIndex> provider = mock(ObjectProvider.class);
        when(provider.iterator()).thenReturn(List.of(indexes).iterator());
        return new SearchIndexBootstrap(manager, properties, provider);
    }

    record Doc(String title) {
    }
}
