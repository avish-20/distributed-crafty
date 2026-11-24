package com.avish.distributed_craftly.workspace_service.service.impl;

import com.avish.distributed_craftly.common_lib.error.ResourceNotFoundException;
import com.avish.distributed_craftly.workspace_service.entity.Project;
import com.avish.distributed_craftly.workspace_service.entity.ProjectFile;
import com.avish.distributed_craftly.workspace_service.repository.ProjectFileRepository;
import com.avish.distributed_craftly.workspace_service.repository.ProjectRepository;
import com.avish.distributed_craftly.workspace_service.service.ProjectTemplateService;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProjectTemplateServiceImpl implements ProjectTemplateService {
    private final MinioClient minioClient;
    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;


    private String TEMPLATE_BUCKET = "starter-projects";;

    @Value("${minio.project-bucket}")
    private String TARGET_BUCKET;

    private static final String TEMPLATE_NAME = "react-vite-tailwind-daisyui-starter";

    @Override
    public void initializeProjectFromProjectTemplate(Long projectId) {
        // Implementation logic to initialize a project from a template
        Project project = projectRepository.findById(projectId).orElseThrow(
                () -> new ResourceNotFoundException("Project", projectId.toString()));

        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(TEMPLATE_BUCKET)
                            .prefix(TEMPLATE_NAME + "/")
                            .recursive(true)
                            .build()
            );

            List<ProjectFile> filesToSave = new ArrayList<>(); // for metadata in postgres db

            for (Result<Item> result : results) {
                Item item = result.get();
                String sourceKey = item.objectName();

                String cleanPath = sourceKey.replaceFirst(TEMPLATE_NAME + "/", "");
                String destKey = projectId + "/" + cleanPath;

                minioClient.copyObject(
                        CopyObjectArgs.builder()
                                .bucket(TARGET_BUCKET)
                                .object(destKey)
                                .source(
                                        CopySource.builder()
                                                .bucket(TEMPLATE_BUCKET)
                                                .object(sourceKey)
                                                .build()
                                )
                                .build()
                );

                ProjectFile pf = ProjectFile.builder()
                        .project(project)
                        .path(cleanPath)
                        .minioObjectKey(destKey)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                filesToSave.add(pf);
            }

            projectFileRepository.saveAll(filesToSave);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize project from template", e);
        }

    }
}
