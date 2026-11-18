package com.example.ty.vision;
import com.example.ty.auth.entity.User;
import com.example.ty.auth.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@RestController @RequestMapping("/api/vision") @RequiredArgsConstructor
public class VisionController {
    private final VisionService service;
    private final CloudVisionService cloud;
    @GetMapping("/model") public Map<String,Object> model(){return service.model();}
    @GetMapping("/cloud") public Map<String,Object> cloudStatus(){return cloud.status();}
    @PostMapping(value="/analyze",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> analyze(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTR) User user,@RequestParam(required=false) String crop,@RequestParam(required=false) String task,@RequestParam(required=false) MultipartFile file,@RequestParam(required=false) MultipartFile image){return cloud.analyze(crop,task,file!=null?file:image);}
    @PostMapping("/jobs") @ResponseStatus(HttpStatus.ACCEPTED)
    public VisionService.View create(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTR) User user,@RequestParam String farmId,@RequestParam String plotId,@RequestParam MultipartFile file){return service.create(user.getId(),farmId,plotId,file);}
    @GetMapping("/jobs") public List<VisionService.View> list(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTR) User user,@RequestParam String farmId){return service.list(user.getId(),farmId);}
    @GetMapping("/jobs/{id}") public VisionService.View get(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTR) User user,@PathVariable String id){return service.get(id,user.getId());}
    @GetMapping("/jobs/{id}/reviews") public List<VisionReview> reviews(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTR) User user,@PathVariable String id){return service.reviews(id,user.getId());}
    @PostMapping("/jobs/{id}/reviews") public VisionReview review(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTR) User user,@PathVariable String id,@jakarta.validation.Valid @RequestBody VisionService.ReviewInput input){return service.review(id,user.getId(),input);}
    @GetMapping("/jobs/{id}/image") public ResponseEntity<byte[]> image(@RequestAttribute(AuthInterceptor.CURRENT_USER_ATTR) User user,@PathVariable String id){
        byte[] bytes=service.image(id,user.getId());MediaType type=bytes.length>3&&bytes[0]==(byte)0x89?MediaType.IMAGE_PNG:MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").body(bytes);
    }
}
