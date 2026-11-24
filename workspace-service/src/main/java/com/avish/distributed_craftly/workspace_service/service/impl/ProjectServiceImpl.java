package com.avish.distributed_craftly.workspace_service.service.impl;

import com.avish.distributed_craftly.common_lib.dto.PlanDto;
import com.avish.distributed_craftly.common_lib.enums.ProjectPermission;
import com.avish.distributed_craftly.common_lib.enums.ProjectRole;
import com.avish.distributed_craftly.common_lib.error.BadRequestException;
import com.avish.distributed_craftly.common_lib.error.ResourceNotFoundException;
import com.avish.distributed_craftly.common_lib.security.AuthUtil;
import com.avish.distributed_craftly.workspace_service.client.AccountClient;
import com.avish.distributed_craftly.workspace_service.dto.project.ProjectRequest;
import com.avish.distributed_craftly.workspace_service.dto.project.ProjectResponse;
import com.avish.distributed_craftly.workspace_service.dto.project.ProjectSummaryResponse;
import com.avish.distributed_craftly.workspace_service.entity.Project;
import com.avish.distributed_craftly.workspace_service.entity.ProjectMember;
import com.avish.distributed_craftly.workspace_service.entity.ProjectMemberId;
import com.avish.distributed_craftly.workspace_service.mapper.ProjectMapper;
import com.avish.distributed_craftly.workspace_service.repository.ProjectMemberRepository;
import com.avish.distributed_craftly.workspace_service.repository.ProjectRepository;
import com.avish.distributed_craftly.workspace_service.security.SecurityExpressions;
import com.avish.distributed_craftly.workspace_service.service.ProjectService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Transactional
public class ProjectServiceImpl implements ProjectService {
    ProjectRepository projectRepository;
    ProjectMapper projectMapper;
    ProjectMemberRepository projectMemberRepository;
    AuthUtil authUtil;
    AccountClient accountClient;
    ProjectTemplateServiceImpl projectTemplateService;
    SecurityExpressions securityExpressions;

    @Override
    public List<ProjectSummaryResponse> getUserProjects() {
        Long userId = authUtil.getCurrentUserId();
        var projectsWithRoles = projectRepository.findAllAccessibleByUser(userId);
        return projectsWithRoles.stream()
                .map(p -> projectMapper.toProjectSummaryResponse(p.getProject(), p.getRole()))
                .toList();
    }


    @Override
    @PreAuthorize("@security.canViewProject(#projectId)")
    public ProjectSummaryResponse getUserProjectById(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
//        Project project = getAccessibleProjectById(projectId, userId);
        var projectWithRole = projectRepository.findAccessibleProjectByIdWithRole(projectId, userId)
                .orElseThrow(() -> new BadRequestException("Project Not Found"));
        return projectMapper.toProjectSummaryResponse(projectWithRole.getProject(),projectWithRole.getRole());
    }

    @Override
    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {

        if(!canCreateProject()){
            throw new BadRequestException("User can not create project with current subscription:");
        }
        Long ownerUserId = authUtil.getCurrentUserId();
        //User owner = userRepository.findById(userId).orElseThrow(()-> new ResourceNotFoundException("User", userId.toString()));
        //For above line we are making db call but we can create proxy reference object with
        // id just from jpa It will not make  adb call unless other filed s of owner required"
        //Make sure to use this with transactional annotation, otherwise it will throw error when we try to save project member because of detached entity
        Project project = Project.builder()
                .name(request.name())
                .isPublic(false)
                .build();
        project = projectRepository.save(project);
        ProjectMemberId projectMemberId = new ProjectMemberId(project.getId(), ownerUserId);

        ProjectMember projectMember = ProjectMember.builder()
                .id(projectMemberId)
                .projectRole(ProjectRole.OWNER)
                .acceptedAt(Instant.now())
                .invitedAt(Instant.now())
                .project(project)
                .build();

        projectMemberRepository.save(projectMember);


        //model mapper does not fully support record
        projectTemplateService.initializeProjectFromProjectTemplate(project.getId());


        return projectMapper.toProjectResponse(project);
    }

    @Override
    @PreAuthorize("@security.canEditProject(#projectId)")
    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);
//        if(!project.getOwner().getId().equals(userId)) {
//            throw new RuntimeException("You are not allowed to update the name");
//        }
        project.setName(request.name());
        project = projectRepository.save(project);
        return projectMapper.toProjectResponse(project);
    }

    @Override
    @PreAuthorize("@security.canDeleteProject(#projectId)")
    public void softDelete(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);
//        if(!project.getOwner().getId().equals(userId)) {
//            throw new RuntimeException("You are not allowed to delete the project");
//        }
        project.setDeletedAt(Instant.now());
        projectRepository.save(project);

    }

    @Override
    public boolean hasPermission(Long projectId, ProjectPermission permission) {
        return securityExpressions.hasPermission(projectId, permission);
    }

    //Internal methods
    public Project getAccessibleProjectById(Long id, Long userId) {
        return projectRepository.findAccessibleById(id, userId).orElseThrow(
                () -> new ResourceNotFoundException("Project", id.toString())
        );
    }
    private boolean canCreateProject() {
        Long userId = authUtil.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        PlanDto plan = accountClient.getCurrentSubscribedPlanByUser();

        int maxAllowed = plan.maxProjects();
        int ownedCount = projectMemberRepository.countProjectOwnedByUser(userId);

        return ownedCount < maxAllowed;
    }
}
