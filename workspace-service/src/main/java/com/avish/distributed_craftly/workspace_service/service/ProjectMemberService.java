package com.avish.distributed_craftly.workspace_service.service;

import com.avish.distributed_craftly.workspace_service.dto.member.InviteMemberRequest;
import com.avish.distributed_craftly.workspace_service.dto.member.MemberResponse;
import com.avish.distributed_craftly.workspace_service.dto.member.UpdateMemberRoleRequest;

import java.util.List;

public interface ProjectMemberService {
    List<MemberResponse> getProjectMembers(Long projectId);

    MemberResponse inviteMember(Long projectId, InviteMemberRequest request);

    MemberResponse updateMemeberRole(Long projectId, Long memberId, UpdateMemberRoleRequest request);

    void removeProjectMember(Long projectId, Long memberId);
}
