package com.windrunner.server.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ApiExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void malformedRequestIsReportedAsBadRequest() {
        when(request.getHeader("x-request-id")).thenReturn("request-1");

        var response = new ApiExceptionHandler().handleBadRequest(
                new HttpMessageNotReadableException("invalid json", mock(HttpInputMessage.class)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errors().get(0).code()).isEqualTo("BAD_REQUEST");
    }
}
