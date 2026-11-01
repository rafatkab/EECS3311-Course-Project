package com.eecs3311.backend.material;

import com.eecs3311.backend.course.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MaterialService {
    private final CourseService courses;
    private final MaterialRepository repository;
    private final MaterialParser parser;

    public MaterialService(CourseService courses, MaterialRepository repository, MaterialParser parser) {
        this.courses = courses;
        this.repository = repository;
        this.parser = parser;
    }

    @Transactional
    public List<LearningMaterial> uploadMaterial(String username, UUID courseId, List<MultipartFile> files) {
        var course = courses.getCourse(username, courseId);
        if (files == null || files.isEmpty() || files.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select between 1 and 10 files per request.");
        }
        List<LearningMaterial> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.getSize() > MaterialParser.MAX_FILE_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Each file must be 10 MiB or smaller.");
            }
            try (var stream = file.getInputStream()) {
                byte[] bytes = stream.readNBytes(MaterialParser.MAX_FILE_BYTES + 1);
                var parsed = parser.parse(file.getOriginalFilename(), bytes);
                var material = course.addMaterial(parsed.fileName(), parsed.fileType(), bytes.length, parsed.text());
                repository.save(material, bytes);
                saved.add(material);
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file.");
            }
        }
        return List.copyOf(saved);
    }

    public List<LearningMaterial> listMaterials(String username, UUID courseId) {
        courses.getCourse(username, courseId);
        return repository.findByCourse(courseId);
    }

    public LearningMaterial getMaterial(String username, UUID courseId, UUID id) {
        courses.getCourse(username, courseId);
        return repository.findByIdAndCourse(id, courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learning material not found."));
    }

    public byte[] download(String username, UUID courseId, UUID id) {
        getMaterial(username, courseId, id);
        return repository.original(id, courseId);
    }
}
