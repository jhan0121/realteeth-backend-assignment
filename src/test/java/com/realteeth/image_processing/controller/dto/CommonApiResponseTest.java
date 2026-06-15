package com.realteeth.image_processing.controller.dto;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.realteeth.image_processing.controller.dto.response.CommonApiResponse;

class CommonApiResponseTest {

    @Test
    @DisplayName("ok()은 success=true, 데이터 페이로드, error=null을 반환한다")
    void ok_setsSuccessTrueAndData() {
        CommonApiResponse<String> response = CommonApiResponse.ok("payload");

        assertSoftly(softly -> {
            softly.assertThat(response.success()).isTrue();
            softly.assertThat(response.data()).isEqualTo("payload");
            softly.assertThat(response.error()).isNull();
            softly.assertThat(response.details()).isNull();
        });
    }

    @Test
    @DisplayName("error()은 success=false, data=null, 에러 메시지를 반환한다")
    void error_setsSuccessFalseAndMessage() {
        CommonApiResponse<Object> response = CommonApiResponse.error("something went wrong");

        assertSoftly(softly -> {
            softly.assertThat(response.success()).isFalse();
            softly.assertThat(response.data()).isNull();
            softly.assertThat(response.error()).isEqualTo("something went wrong");
            softly.assertThat(response.details()).isNull();
        });
    }

    @Test
    @DisplayName("ok()에 null 데이터를 전달해도 허용된다")
    void ok_withNullData_isAllowed() {
        CommonApiResponse<Object> response = CommonApiResponse.ok(null);

        assertSoftly(softly -> {
            softly.assertThat(response.success()).isTrue();
            softly.assertThat(response.data()).isNull();
            softly.assertThat(response.error()).isNull();
            softly.assertThat(response.details()).isNull();
        });
    }

    @Test
    @DisplayName("errors()는 success=false, 요약 메시지, 필드별 상세 목록을 반환한다")
    void errors_setsSuccessFalseWithSummaryAndDetails() {
        List<String> fieldErrors = List.of("imageUrl: 필수입니다", "userId: 필수입니다");
        CommonApiResponse<Object> response = CommonApiResponse.errors("입력값이 유효하지 않습니다", fieldErrors);

        assertSoftly(softly -> {
            softly.assertThat(response.success()).isFalse();
            softly.assertThat(response.data()).isNull();
            softly.assertThat(response.error()).isEqualTo("입력값이 유효하지 않습니다");
            softly.assertThat(response.details()).containsExactly("imageUrl: 필수입니다", "userId: 필수입니다");
        });
    }
}
