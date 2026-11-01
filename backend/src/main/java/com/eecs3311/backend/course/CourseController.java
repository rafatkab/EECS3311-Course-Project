package com.eecs3311.backend.course;

import jakarta.validation.Valid;
import com.eecs3311.backend.material.LearningMaterial;
import com.eecs3311.backend.material.MaterialService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService service;
    private final MaterialService materials;
    public CourseController(CourseService service, MaterialService materials) {
        this.service = service;
        this.materials = materials;
    }

    @PostMapping
    public ResponseEntity<Course> createCourse(Principal principal, @Valid @RequestBody CreateCourseRequest request) {
        Course course = service.createCourse(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/courses/" + course.courseId())).body(course);
    }

    @GetMapping
    public List<Course> viewCourses(Principal principal) { return service.viewCourses(principal.getName()); }

    @GetMapping("/{id}")
    public Course getCourse(Principal principal, @PathVariable UUID id) {
        return service.getCourse(principal.getName(), id);
    }

    @PostMapping(path = "/{id}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<LearningMaterial>> uploadMaterial(Principal principal, @PathVariable UUID id,
                                                                @RequestParam("files") List<MultipartFile> files) {
        var result = materials.uploadMaterial(principal.getName(), id, files);
        return ResponseEntity.created(URI.create("/api/courses/" + id + "/materials")).body(result);
    }

    @GetMapping("/{id}/materials")
    public List<LearningMaterial> listMaterials(Principal principal, @PathVariable UUID id) {
        return materials.listMaterials(principal.getName(), id);
    }

    @GetMapping(path = "/{id}/materials/{materialId}/content", produces = "text/plain;charset=UTF-8")
    public String materialContent(Principal principal, @PathVariable UUID id, @PathVariable UUID materialId) {
        return materials.getMaterial(principal.getName(), id, materialId).getContent();
    }

    @GetMapping("/{id}/materials/{materialId}/download")
    public ResponseEntity<byte[]> downloadMaterial(Principal principal, @PathVariable UUID id, @PathVariable UUID materialId) {
        var material = materials.getMaterial(principal.getName(), id, materialId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(material.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("Cache-Control", "no-store")
                .body(materials.download(principal.getName(), id, materialId));
    }
}
