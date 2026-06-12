package com.realteeth.image_processing.controller.dto;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    @DisplayName("ok()은 success=true, 데이터 페이로드, error=null을 반환한다")
    void ok_setsSuccessTrueAndData() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertSoftly(softly -> {
            softly.assertThat(response.success()).isTrue();
            softly.assertThat(response.data()).isEqualTo("payload");
            softly.assertThat(response.error()).isNull();
        });
    }

    @Test
    @DisplayName("error()은 success=false, data=null, 에러 메시지를 반환한다")
    void error_setsSuccessFalseAndMessage() {
        ApiResponse<Object> response = ApiResponse.error("something went wrong");

        assertSoftly(softly -> {
            softly.assertThat(response.success()).isFalse();
            softly.assertThat(response.data()).isNull();
            softly.assertThat(response.error()).isEqualTo("something went wrong");
        });
    }

    @Test
    @DisplayName("ok()에 null 데이터를 전달해도 허용된다")
    void ok_withNullData_isAllowed() {
        ApiResponse<Object> response = ApiResponse.ok(null);

        assertSoftly(softly -> {
            softly.assertThat(response.success()).isTrue();
            softly.assertThat(response.data()).isNull();
            softly.assertThat(response.error()).isNull();
        });
    }
}
