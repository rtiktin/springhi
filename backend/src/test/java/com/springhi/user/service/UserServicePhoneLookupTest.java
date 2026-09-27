package com.springhi.user.service;

import com.springhi.user.dto.ProfileRequest;
import com.springhi.user.model.User;
import com.springhi.user.model.UserPhoneHistory;
import com.springhi.user.repository.UserPhoneHistoryRepository;
import com.springhi.user.repository.UserRepository;
import com.springhi.user.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class UserServicePhoneLookupTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserPhoneHistoryRepository history = mock(UserPhoneHistoryRepository.class);
    private final UserSubscriptionRepository subscriptions = mock(UserSubscriptionRepository.class);
    private final UserService service = new UserService(users, null, null, subscriptions, history);

    @Test
    void matchesCurrentAndPreviousPhonesAcrossUsersWithoutReturningCaller() {
        User caller = new User();
        caller.setId(1L);
        caller.setPhone("(212) 555-0199");
        UserPhoneHistory previous = new UserPhoneHistory();
        previous.setPhone("+1 415-555-0177");
        User linked = new User();
        linked.setId(2L);
        when(users.findByUsername("caller")).thenReturn(Optional.of(caller));
        when(history.findByUserId(1L)).thenReturn(List.of(previous));
        when(users.findIdsByPhoneDigitsIn(anyCollection())).thenReturn(List.of(1L, 2L));
        when(history.findUserIdsByPhoneDigitsIn(anyCollection())).thenReturn(List.of(2L));
        when(users.findAllById(anyCollection())).thenReturn(List.of(linked));
        when(subscriptions.findByUserIdIn(List.of(2L))).thenReturn(List.of());

        var result = service.getLinkedPhoneSignupStatuses("caller");

        assertEquals(Set.of(2L), result.keySet());
        assertFalse(result.get(2L).subscribed());
        verify(users).findIdsByPhoneDigitsIn(argThat(numbers -> numbers.containsAll(
                Set.of("2125550199", "12125550199", "4155550177", "14155550177"))));
    }

    @Test
    void profileUpdateCannotReplaceVerifiedPhoneWithoutSmsVerification() {
        User caller = new User();
        caller.setPhone("+12125550199");
        caller.setPhoneVerified(true);
        when(users.findByUsername("caller")).thenReturn(Optional.of(caller));
        ProfileRequest request = new ProfileRequest();
        request.setPhone("+14155550177");

        assertThrows(RuntimeException.class, () -> service.updateProfile("caller", request));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void missingPhoneDoesNotMatchEmptyValues() {
        User caller = new User();
        caller.setId(1L);
        when(users.findByUsername("caller")).thenReturn(Optional.of(caller));
        when(history.findByUserId(1L)).thenReturn(List.of());

        assertTrue(service.getLinkedPhoneSignupStatuses("caller").isEmpty());
        verify(users, never()).findIdsByPhoneDigitsIn(anyCollection());
    }
}
