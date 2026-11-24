package com.avish.distributed_craftly.workspace_service.mapper;

import com.avish.distributed_craftly.common_lib.dto.FileNode;
import com.avish.distributed_craftly.workspace_service.entity.ProjectFile;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectFileMapper {
    List<FileNode> toListOfFileNode(List<ProjectFile> files);
}
