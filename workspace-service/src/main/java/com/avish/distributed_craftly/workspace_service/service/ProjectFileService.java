package com.avish.distributed_craftly.workspace_service.service;


import com.avish.distributed_craftly.common_lib.dto.FileTreeDto;
import com.avish.distributed_craftly.workspace_service.dto.project.FileContentResponse;

public interface ProjectFileService {
    FileTreeDto getFileTree(Long projectId);

    String getFileContent(Long projectId, String path);

    void saveFile(Long projectId, String filePath, String fileContent);
}
