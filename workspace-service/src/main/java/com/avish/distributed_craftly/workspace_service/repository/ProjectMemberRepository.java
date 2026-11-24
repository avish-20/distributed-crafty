package com.avish.distributed_craftly.workspace_service.repository;

import com.avish.distributed_craftly.common_lib.enums.ProjectRole;
import com.avish.distributed_craftly.workspace_service.entity.ProjectMember;
import com.avish.distributed_craftly.workspace_service.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    List<ProjectMember> findByIdProjectId(Long projectId);

    @Override
    Optional<ProjectMember> findById(ProjectMemberId projectMemberId);


    @Query("SELECT pm.projectRole FROM ProjectMember pm " +
            "where pm.id.projectId = :projectId and pm.id.userId=:userId")
    Optional<ProjectRole> findRoleByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Query("SELECT COUNT(pm) FROM ProjectMember pm WHERE pm.id.userId = :userId AND pm.projectRole = 'OWNER'")
    int countProjectOwnedByUser(@Param("userId") Long userId);
}
