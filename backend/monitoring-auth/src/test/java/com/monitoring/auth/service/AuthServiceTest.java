package com.monitoring.auth.service;

import com.monitoring.auth.dto.UserProfileResponse;
import com.monitoring.auth.entity.User;
import com.monitoring.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void getProfile_returnsUserProfile() {
        User user = User.builder()
                .id(7L)
                .username("ada")
                .email("ada@example.com")
                .avatarUrl("https://cdn.example.com/ada.png")
                .build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        UserProfileResponse profile = authService.getProfile(7L);

        assertThat(profile.getId()).isEqualTo(7L);
        assertThat(profile.getUsername()).isEqualTo("ada");
        assertThat(profile.getEmail()).isEqualTo("ada@example.com");
        assertThat(profile.getAvatarUrl()).isEqualTo("https://cdn.example.com/ada.png");
    }

    @Test
    void getProfile_returnsNullAvatarWhenNotSet() {
        User user = User.builder()
                .id(8L)
                .username("grace")
                .email("grace@example.com")
                .build();
        given(userRepository.findById(8L)).willReturn(Optional.of(user));

        UserProfileResponse profile = authService.getProfile(8L);

        assertThat(profile.getAvatarUrl()).isNull();
    }

    @Test
    void getProfile_throwsWhenUserMissing() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getProfile(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }
}
