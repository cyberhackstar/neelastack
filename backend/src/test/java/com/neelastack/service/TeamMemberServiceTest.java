package com.neelastack.service;

import com.cloudinary.Cloudinary;
import com.neelastack.entity.Role;
import com.neelastack.exception.BadRequestException;
import com.neelastack.repository.TeamMemberRepository;
import com.neelastack.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TeamMemberServiceTest {
    private TeamMemberRepository repository;
    private CurrentUserProvider currentUserProvider;
    private Cloudinary cloudinary;
    private TeamMemberService service;

    @BeforeEach
    void setUp() {
        repository = mock(TeamMemberRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        cloudinary = mock(Cloudinary.class);
        var user = mock(com.neelastack.entity.User.class);
        when(user.getRole()).thenReturn(Role.SUPERADMIN);
        when(currentUserProvider.get()).thenReturn(user);
        service = new TeamMemberService(repository, currentUserProvider, cloudinary);
    }

    @Test
    void create_requiresProfilePhoto() {
        assertThatThrownBy(() -> service.create("B", "Engineer", "Bio", "Spring Boot", 0, true, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Profile photo is required");
        verifyNoInteractions(repository, cloudinary);
    }

    @Test
    void create_rejectsNegativeSortOrderBeforeUpload() {
        var photo = new org.springframework.mock.web.MockMultipartFile(
                "photo", "team.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.create("B", "Engineer", "Bio", "Spring Boot", -1, true, photo))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Display order");
        verifyNoInteractions(repository, cloudinary);
    }

    @Test
    void create_rejectsOversizedSortOrderBeforeUpload() {
        var photo = new org.springframework.mock.web.MockMultipartFile(
                "photo", "team.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> service.create("B", "Engineer", "Bio", "Spring Boot", 100001, true, photo))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Display order");
        verifyNoInteractions(repository, cloudinary);
    }
}
