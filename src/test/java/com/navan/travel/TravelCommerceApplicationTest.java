package com.navan.travel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

public class TravelCommerceApplicationTest {

    @Test
    void testContextLoadsAndMain() {
        assertThatNoException().isThrownBy(() -> {
            TravelCommerceApplication.main(new String[]{
                    "--spring.main.web-application-type=none",
                    "--spring.profiles.active=test"
            });
        });
    }
}
