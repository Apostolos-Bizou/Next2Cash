package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.CalendarResponse;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.service.CalendarService;
import com.next2me.next2cash.service.UserAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CalendarControllerTest {

    private static final String AUTH = "Bearer fake.jwt.token";

    private CalendarController newCtrl(CalendarService svc, UserAccessService uas) {
        return new CalendarController(svc, uas);
    }

    private User adminUser() {
        User u = new User();
        u.setRole("admin");
        return u;
    }

    private User regularUser() {
        User u = new User();
        u.setRole("user");
        return u;
    }

    @Test
    public void rejectsInvalidYear() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        when(uas.getCurrentUser(anyString())).thenReturn(adminUser());

        CalendarController ctrl = newCtrl(svc, uas);
        ResponseEntity<CalendarResponse> resp = ctrl.getCalendar("ALL", 1999, 6, AUTH);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    public void rejectsInvalidMonth() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        when(uas.getCurrentUser(anyString())).thenReturn(adminUser());

        CalendarController ctrl = newCtrl(svc, uas);
        ResponseEntity<CalendarResponse> resp = ctrl.getCalendar("ALL", 2026, 13, AUTH);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    public void groupViewRequiresAdmin() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        when(uas.getCurrentUser(anyString())).thenReturn(regularUser());

        CalendarController ctrl = newCtrl(svc, uas);
        ResponseEntity<CalendarResponse> resp = ctrl.getCalendar("ALL", 2026, 6, AUTH);
        assertEquals(403, resp.getStatusCode().value());
    }

    @Test
    public void groupViewAllowedForAdmin() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        when(uas.getCurrentUser(anyString())).thenReturn(adminUser());

        CalendarResponse fake = new CalendarResponse();
        fake.setYear(2026);
        fake.setMonth(6);
        fake.setEntityScope("group");
        when(svc.buildGroupCalendar(2026, 6)).thenReturn(fake);

        CalendarController ctrl = newCtrl(svc, uas);
        ResponseEntity<CalendarResponse> resp = ctrl.getCalendar("ALL", 2026, 6, AUTH);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("group", resp.getBody().getEntityScope());
    }

    @Test
    public void rejectsMalformedEntityId() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        when(uas.getCurrentUser(anyString())).thenReturn(regularUser());

        CalendarController ctrl = newCtrl(svc, uas);
        ResponseEntity<CalendarResponse> resp = ctrl.getCalendar("not-a-uuid", 2026, 6, AUTH);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    public void entityViewCallsServiceWithUuid() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        User user = regularUser();
        when(uas.getCurrentUser(anyString())).thenReturn(user);

        UUID eid = UUID.fromString("58202b71-0000-0000-0000-000000000000");
        CalendarResponse fake = new CalendarResponse();
        fake.setYear(2026);
        fake.setMonth(6);
        fake.setEntityScope("single");
        when(svc.buildEntityCalendar(eid, 2026, 6)).thenReturn(fake);

        CalendarController ctrl = newCtrl(svc, uas);
        ResponseEntity<CalendarResponse> resp = ctrl.getCalendar(eid.toString(), 2026, 6, AUTH);
        assertEquals(200, resp.getStatusCode().value());
        verify(uas, times(1)).assertCanAccessEntity(user, eid);
        verify(svc, times(1)).buildEntityCalendar(eid, 2026, 6);
    }
}
