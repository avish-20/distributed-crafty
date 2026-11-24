package com.avish.distributed_craftly.workspace_service.service.impl;

import com.avish.distributed_craftly.common_lib.dto.UserDto;
import com.avish.distributed_craftly.common_lib.error.ResourceNotFoundException;
import com.avish.distributed_craftly.common_lib.security.AuthUtil;
import com.avish.distributed_craftly.workspace_service.client.AccountClient;
import com.avish.distributed_craftly.workspace_service.dto.member.InviteMemberRequest;
import com.avish.distributed_craftly.workspace_service.dto.member.MemberResponse;
import com.avish.distributed_craftly.workspace_service.dto.member.UpdateMemberRoleRequest;
import com.avish.distributed_craftly.workspace_service.entity.Project;
import com.avish.distributed_craftly.workspace_service.entity.ProjectMember;
import com.avish.distributed_craftly.workspace_service.entity.ProjectMemberId;
import com.avish.distributed_craftly.workspace_service.mapper.ProjectMemberMapper;
import com.avish.distributed_craftly.workspace_service.repository.ProjectMemberRepository;
import com.avish.distributed_craftly.workspace_service.repository.ProjectRepository;
import com.avish.distributed_craftly.workspace_service.service.ProjectMemberService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Transactional
public class ProjectMemberServiceImpl implements ProjectMemberService {
    ProjectMemberRepository projectMemberRepository;
    ProjectRepository projectRepository;
    ProjectMemberMapper projectMemberMapper;
    AuthUtil authUtil;
    AccountClient accountClient;


    @Override
    @PreAuthorize("@security.canViewProject(#projectId)")
    public List<MemberResponse> getProjectMembers(Long projectId) {
        List<MemberResponse> memberResponseList = new ArrayList<>();
        //memberResponseList.add(projectMemberMapper.toProjectMemberResponseFromOwner(project.getOwner()));

        projectMemberRepository.findByIdProjectId(projectId)
                .stream()
                .map(projectMemberMapper::toProjectMemberResponseFromMember)
                .forEach(memberResponseList::add);
        return memberResponseList;
    }

    @Override
    @PreAuthorize("@security.canManageMembers(#projectId)")
    public MemberResponse inviteMember(Long projectId, InviteMemberRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);
//        if (!project.getOwner().getId().equals(userId)) {
//            throw new RuntimeException("Not allowed");
//        }
        UserDto invitee = accountClient.getUserByEmail(request.username()).orElseThrow(
                () -> new ResourceNotFoundException("User not found with email: " ,request.username())
        );
        if (invitee.id().equals(userId)) {
            throw new RuntimeException("You cannot invite yourself");
        }
        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, invitee.id());
        if (projectMemberRepository.existsById(projectMemberId)) {
            throw new RuntimeException("User is already a member");
        }
        ProjectMember projectMember = ProjectMember.builder()
                .id(projectMemberId)
                .project(project)
                .projectRole(request.role())
                .invitedAt(Instant.now())
                .build();
        projectMemberRepository.save(projectMember);
        return projectMemberMapper.toProjectMemberResponseFromMember(projectMember);

    }

    @Override
    @PreAuthorize("@security.canManageMembers(#projectId)")
    public MemberResponse updateMemeberRole(Long projectId, Long memberId, UpdateMemberRoleRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);
//        if (!project.getOwner().getId().equals(userId)) {
//            throw new RuntimeException("Not allowed");
//        }
        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, memberId);
        ProjectMember projectMember = projectMemberRepository.findById(projectMemberId).orElseThrow();
        System.out.println(projectMember);
        projectMember.setProjectRole(request.role());
        projectMemberRepository.save(projectMember);
        return projectMemberMapper.toProjectMemberResponseFromMember(projectMember);
    }

    @Override
    @PreAuthorize("@security.canManageMembers(#projectId)")
    public void removeProjectMember(Long projectId, Long memberId) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);
//        if (!project.getOwner().getId().equals(userId)) {
//            throw new RuntimeException("Not allowed");
//        }
        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, memberId);
        if(!projectMemberRepository.existsById(projectMemberId)) {
            throw new RuntimeException("User is not a member");
        }
        projectMemberRepository.deleteById(projectMemberId);
        //return projectMemberMapper.toProjectMemberResponseFromMember(projectMember);
    }

    //Internal methods
    public Project getAccessibleProjectById(Long id, Long userId) {
        return projectRepository.findAccessibleById(id, userId).orElseThrow();
    }
}
