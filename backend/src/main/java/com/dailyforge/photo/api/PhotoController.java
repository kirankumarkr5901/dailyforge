package com.dailyforge.photo.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.photo.domain.PhotoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Progress photos: an upload link, recording the upload, listing and deleting. Logging, not structure. */
@RestController
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private final PhotoService photos;
    private final CurrentUser currentUser;

    public PhotoController(PhotoService photos, CurrentUser currentUser) {
        this.photos = photos;
        this.currentUser = currentUser;
    }

    public record PhotosResponse(boolean enabled, List<PhotoService.Photo> photos) {}

    public record UploadUrlRequest(@NotNull Long sizeBytes) {}

    public record RecordRequest(@NotBlank String key, @NotNull LocalDate takenOn) {}

    @GetMapping
    public PhotosResponse list() {
        UUID userId = currentUser.require();
        return new PhotosResponse(photos.enabled(), photos.list(userId));
    }

    @PostMapping("/upload-url")
    public PhotoService.Upload uploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return photos.uploadUrl(currentUser.require(), request.sizeBytes());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoService.Photo record(@Valid @RequestBody RecordRequest request) {
        return photos.record(currentUser.require(), request.key(), request.takenOn());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        photos.delete(currentUser.require(), id);
    }
}
