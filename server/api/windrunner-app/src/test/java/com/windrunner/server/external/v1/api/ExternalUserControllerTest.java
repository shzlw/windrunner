package com.windrunner.server.external.v1.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windrunner.server.api.ApiResponse;
import com.windrunner.server.apikey.ApiKeyScopes;
import com.windrunner.server.external.auth.ExternalAccessService;
import com.windrunner.server.external.v1.dto.ExternalUserIdentityResponse;
import com.windrunner.server.user.UserStatuses;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ExternalUserControllerTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ExternalAccessService externalAccessService;
    @Mock
    private HttpServletRequest request;

    @Test
    void getUserReturnsIdentityAndProfileFields() {
        AppUser user = new AppUser();
        user.setId("user-1");
        user.setUsername("jane");
        user.setDisplayName("Jane Doe");
        user.setTitle("Product manager");
        user.setBio("Helps teams prioritize customer problems.");
        user.setEmail("jane@example.com");
        user.setStatus(UserStatuses.ACTIVE);
        user.setGlobalRole("ADMIN");

        when(appUserRepository.findById("user-1")).thenReturn(Optional.of(user));

        ApiResponse<ExternalUserIdentityResponse> response = controller().getUser("user-1", request);

        verify(externalAccessService).requireScope(request, ApiKeyScopes.USERS_READ);
        assertThat(response.data()).isEqualTo(
                new ExternalUserIdentityResponse(
                        "user-1",
                        "jane",
                        "Jane Doe",
                        "Product manager",
                        "Helps teams prioritize customer problems."));
        assertThat(response.data()).isInstanceOf(ExternalUserIdentityResponse.class);
    }

    @Test
    void getUserDoesNotExposeInactiveUsers() {
        AppUser user = new AppUser();
        user.setId("user-1");
        user.setStatus(UserStatuses.INACTIVE);
        when(appUserRepository.findById("user-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller().getUser("user-1", request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getUnknownUserReturns404() {
        when(appUserRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().getUser("missing", request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private ExternalUserController controller() {
        return new ExternalUserController(appUserRepository, externalAccessService);
    }
}
