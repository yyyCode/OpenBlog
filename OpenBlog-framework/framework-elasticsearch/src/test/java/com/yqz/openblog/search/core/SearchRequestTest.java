package com.yqz.openblog.search.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchRequestTest {

    @Test
    void keywordWithoutFieldsIsRejected() {
        assertThatThrownBy(() -> SearchRequest.builder().keyword("es").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fields");
    }

    @Test
    void blankKeywordWithoutFieldsIsAllowed() {
        SearchRequest request = SearchRequest.builder().keyword("   ").build();

        assertThat(request.hasKeyword()).isFalse();
    }

    @Test
    void invalidPageAndSizeAreRejected() {
        assertThatThrownBy(() -> SearchRequest.builder().page(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SearchRequest.builder().size(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderKeepsFiltersFieldsAndSorts() {
        SearchRequest request = SearchRequest.builder()
                .keyword(" spring ")
                .field("title", 3f)
                .termFilter("status", "PUBLISHED")
                .sort(SearchSort.desc("publishedAt"))
                .page(1)
                .size(10)
                .build();

        assertThat(request.getKeyword()).isEqualTo("spring");
        assertThat(request.hasKeyword()).isTrue();
        assertThat(request.getFields()).containsEntry("title", 3f);
        assertThat(request.getTermFilters()).containsEntry("status", "PUBLISHED");
        assertThat(request.getSorts()).hasSize(1);
        assertThat(request.getSorts().get(0).getDirection()).isEqualTo(SearchSort.Direction.DESC);
        assertThat(request.getPage()).isEqualTo(1);
        assertThat(request.getSize()).isEqualTo(10);
    }
}
