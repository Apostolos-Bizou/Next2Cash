package com.next2me.next2cash.repository;

import com.next2me.next2cash.model.Project;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void contextLoads_andRepositoryIsAvailable() {
        assertNotNull(projectRepository, "ProjectRepository must be injected");
    }

    @Test
    void canQueryAllProjectsWithoutError() {
        // Should not throw; result may be empty in H2 test profile
        List<Project> all = projectRepository.findAllOrdered();
        assertNotNull(all, "Result list must not be null");
    }

    @Test
    void canQueryActiveProjectsWithoutError() {
        List<Project> active = projectRepository.findAllActive();
        assertNotNull(active);
    }
}
