package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.CalendarResponse;
import com.next2me.next2cash.service.CalendarService;
import com.next2me.next2cash.service.UserAccessService;
import com.next2me.next2cash.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * S96 — CalendarController sanity tests.
 *
 * Note: lightweight unit-style tests using mocks. Integration tests
 * with @SpringBootTest are intentionally avoided here to keep the
 * test suite fast and self-contained.
 */
public class CalendarControllerTest {

    @Test
    public void rejectsInvalidYear() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        CalendarController ctrl = new CalendarController(svc, uas);

        User admin = new User();
        admin.setRole("ADMIN");

        ResponseEntity<CalendarResponse> resp =
                ctrl.getCalendar("ALL", 1999, 6, admin);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    public void rejectsInvalidMonth() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        CalendarController ctrl = new CalendarController(svc, uas);

        User admin = new User();
        admin.setRole("ADMIN");

        ResponseEntity<CalendarResponse> resp =
                ctrl.getCalendar("ALL", 2026, 13, admin);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    public void groupViewRequiresAdmin() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        CalendarController ctrl = new CalendarController(svc, uas);

        User user = new User();
        user.setRole("USER");

        ResponseEntity<CalendarResponse> resp =
                ctrl.getCalendar("ALL", 2026, 6, user);
        assertEquals(403, resp.getStatusCode().value());
    }

    @Test
    public void groupViewAllowedForAdmin() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        CalendarController ctrl = new CalendarController(svc, uas);

        CalendarResponse fake = new CalendarResponse();
        fake.setYear(2026);
        fake.setMonth(6);
        fake.setEntityScope("group");
        when(svc.buildGroupCalendar(2026, 6)).thenReturn(fake);

        User admin = new User();
        admin.setRole("ADMIN");

        ResponseEntity<CalendarResponse> resp =
                ctrl.getCalendar("ALL", 2026, 6, admin);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertEquals("group", resp.getBody().getEntityScope());
    }

    @Test
    public void rejectsMalformedEntityId() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        CalendarController ctrl = new CalendarController(svc, uas);

        User user = new User();
        user.setRole("USER");

        ResponseEntity<CalendarResponse> resp =
                ctrl.getCalendar("not-a-uuid", 2026, 6, user);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    public void entityViewCallsServiceWithUuid() {
        CalendarService svc = mock(CalendarService.class);
        UserAccessService uas = mock(UserAccessService.class);
        CalendarController ctrl = new CalendarController(svc, uas);

        UUID eid = UUID.fromString("58202b71-0000-0000-0000-000000000000");
        CalendarResponse fake = new CalendarResponse();
        fake.setYear(2026);
        fake.setMonth(6);
        fake.setEntityScope("single");
        when(svc.buildEntityCalendar(eid, 2026, 6)).thenReturn(fake);

        User user = new User();
        user.setRole("USER");

        ResponseEntity<CalendarResponse> resp =
                ctrl.getCalendar(eid.toString(), 2026, 6, user);
        assertEquals(200, resp.getStatusCode().value());
        verify(uas, times(1)).assertCanAccessEntity(user, eid);
        verify(svc, times(1)).buildEntityCalendar(eid, 2026, 6);
    }
}
