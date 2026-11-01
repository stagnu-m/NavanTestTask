package com.navan.travel.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OffsetLimitRequestTest {

    @Test
    void testValidConstructionAndGetters() {
        OffsetLimitRequest req = new OffsetLimitRequest(20, 40, Sort.by("id"));
        assertThat(req.getPageSize()).isEqualTo(20);
        assertThat(req.getOffset()).isEqualTo(40);
        assertThat(req.getPageNumber()).isEqualTo(2);
        assertThat(req.getSort()).isEqualTo(Sort.by("id"));
        assertThat(req.hasPrevious()).isTrue();
    }

    @Test
    void testInvalidParameters() {
        assertThatThrownBy(() -> new OffsetLimitRequest(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit must be greater than zero");

        assertThatThrownBy(() -> new OffsetLimitRequest(10, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Offset index must not be less than zero");
    }

    @Test
    void testNavigationMethods() {
        OffsetLimitRequest req = new OffsetLimitRequest(10, 20);
        Pageable next = req.next();
        assertThat(next.getOffset()).isEqualTo(30);

        Pageable prev = req.previous();
        assertThat(prev.getOffset()).isEqualTo(10);

        Pageable first = req.first();
        assertThat(first.getOffset()).isEqualTo(0);

        Pageable withPage = req.withPage(5);
        assertThat(withPage.getOffset()).isEqualTo(50);

        Pageable prevOrFirst = req.previousOrFirst();
        assertThat(prevOrFirst.getOffset()).isEqualTo(10);

        // hasPrevious false on first page
        OffsetLimitRequest firstReq = new OffsetLimitRequest(10, 0);
        assertThat(firstReq.hasPrevious()).isFalse();
        assertThat(firstReq.previous()).isEqualTo(firstReq);
        assertThat(firstReq.previousOrFirst()).isEqualTo(firstReq);
    }
}
