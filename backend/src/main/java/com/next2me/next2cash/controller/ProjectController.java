package com.next2me.next2cash.controller;

import com.next2me.next2cash.dto.ProjectDTO;
import com.next2me.next2cash.model.Project;
import com.next2me.next2cash.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
@PreAuthorize("isAuthenticated()")
public class ProjectController {

    @Autowired
    private ProjectRepository projectRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listProjects(
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly,
            @RequestParam(value = "status", required = false) String status) {

        List<Project> projects;
        if (status != null && !status.isBlank()) {
            projects = projectRepository.findByStatus(status.toUpperCase());
        } else if (activeOnly) {
            projects = projectRepository.findAllActive();
        } else {
            projects = projectRepository.findAllOrdered();
        }

        List<ProjectDTO> dtos = projects.stream()
                .map(ProjectDTO::fromEntity)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", dtos);
        response.put("count", dtos.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProject(@PathVariable UUID id) {
        Optional<Project> opt = projectRepository.findById(id);
        Map<String, Object> response = new HashMap<>();
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Project not found");
            return ResponseEntity.status(404).body(response);
        }
        response.put("success", true);
        response.put("data", ProjectDTO.fromEntity(opt.get()));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createProject(@RequestBody ProjectDTO dto) {
        Map<String, Object> response = new HashMap<>();
        if (dto.name == null || dto.name.isBlank()) {
            response.put("success", false);
            response.put("error", "Project name is required");
            return ResponseEntity.badRequest().body(response);
        }
        if (dto.ownerEntityId == null) {
            response.put("success", false);
            response.put("error", "ownerEntityId is required");
            return ResponseEntity.badRequest().body(response);
        }
        if (projectRepository.findByName(dto.name).isPresent()) {
            response.put("success", false);
            response.put("error", "Project with this name already exists");
            return ResponseEntity.status(409).body(response);
        }

        Project p = new Project();
        p.setName(dto.name);
        p.setDescription(dto.description);
        p.setOwnerEntityId(dto.ownerEntityId);
        p.setStatus(dto.status != null ? dto.status.toUpperCase() : "PLANNING");
        p.setStartDate(dto.startDate);
        p.setTargetCompletionDate(dto.targetCompletionDate);
        p.setActualCompletionDate(dto.actualCompletionDate);
        p.setTotalBudget(dto.totalBudget);
        p.setExpectedMonthlyRevenue(dto.expectedMonthlyRevenue);
        if (dto.color != null && !dto.color.isBlank()) p.setColor(dto.color);
        if (dto.isActive != null) p.setIsActive(dto.isActive);

        Project saved = projectRepository.save(p);
        response.put("success", true);
        response.put("data", ProjectDTO.fromEntity(saved));
        return ResponseEntity.status(201).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateProject(@PathVariable UUID id, @RequestBody ProjectDTO dto) {
        Map<String, Object> response = new HashMap<>();
        Optional<Project> opt = projectRepository.findById(id);
        if (opt.isEmpty()) {
            response.put("success", false);
            response.put("error", "Project not found");
            return ResponseEntity.status(404).body(response);
        }
        Project p = opt.get();
        if (dto.name != null && !dto.name.isBlank()) p.setName(dto.name);
        if (dto.description != null) p.setDescription(dto.description);
        if (dto.ownerEntityId != null) p.setOwnerEntityId(dto.ownerEntityId);
        if (dto.status != null && !dto.status.isBlank()) p.setStatus(dto.status.toUpperCase());
        if (dto.startDate != null) p.setStartDate(dto.startDate);
        if (dto.targetCompletionDate != null) p.setTargetCompletionDate(dto.targetCompletionDate);
        if (dto.actualCompletionDate != null) p.setActualCompletionDate(dto.actualCompletionDate);
        if (dto.totalBudget != null) p.setTotalBudget(dto.totalBudget);
        if (dto.expectedMonthlyRevenue != null) p.setExpectedMonthlyRevenue(dto.expectedMonthlyRevenue);
        if (dto.color != null && !dto.color.isBlank()) p.setColor(dto.color);
        if (dto.isActive != null) p.setIsActive(dto.isActive);

        Project saved = projectRepository.save(p);
        response.put("success", true);
        response.put("data", ProjectDTO.fromEntity(saved));
        return ResponseEntity.ok(response);
    }
}
