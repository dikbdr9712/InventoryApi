package com.api.inventory.controller;

import com.api.inventory.service.SiteService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * The website's own pages that staff can change.
 *   GET  /api/site/about                         everyone: the About page (texts, live numbers, the team)
 *   site.manage:
 *   GET  /api/site/admin/about                   texts, every team member (hidden too), the original wording
 *   PUT  /api/site/admin/about                   {intro, mission, vision, showNumbers}
 *   POST /api/site/admin/team                    form: name, role, bio, visible, photo (optional)
 *   PUT  /api/site/admin/team/{id}               form: the same, and removePhoto=true to go back to initials
 *   DELETE /api/site/admin/team/{id}
 *   PUT  /api/site/admin/team/order              {ids: [first, ..., last]}
 */
@RestController
@RequestMapping("/api/site")
public class SiteController {

    private final SiteService site;

    public SiteController(SiteService site) {
        this.site = site;
    }

    @GetMapping("/about")
    public SiteService.About about() {
        return site.about();
    }

    @GetMapping("/admin/about")
    @PreAuthorize("hasAuthority('site.manage')")
    public SiteService.AdminAbout forStaff() {
        return site.forStaff();
    }

    @PutMapping("/admin/about")
    @PreAuthorize("hasAuthority('site.manage')")
    public SiteService.AdminAbout saveTexts(@RequestBody SiteService.Texts texts) {
        return site.saveTexts(texts);
    }

    @PostMapping(value = "/admin/team", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('site.manage')")
    public SiteService.Person addPerson(@RequestParam String name, @RequestParam String role,
                                        @RequestParam(required = false) String bio,
                                        @RequestParam(required = false) Boolean visible,
                                        @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return site.addPerson(name, role, bio, visible, photo);
    }

    @PutMapping(value = "/admin/team/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('site.manage')")
    public SiteService.Person updatePerson(@PathVariable Long id, @RequestParam String name, @RequestParam String role,
                                           @RequestParam(required = false) String bio,
                                           @RequestParam(required = false) Boolean visible,
                                           @RequestParam(required = false, defaultValue = "false") boolean removePhoto,
                                           @RequestPart(value = "photo", required = false) MultipartFile photo) {
        return site.updatePerson(id, name, role, bio, visible, photo, removePhoto);
    }

    @DeleteMapping("/admin/team/{id}")
    @PreAuthorize("hasAuthority('site.manage')")
    public Map<String, String> removePerson(@PathVariable Long id) {
        site.removePerson(id);
        return Map.of("message", "Removed from the team.");
    }

    @PutMapping("/admin/team/order")
    @PreAuthorize("hasAuthority('site.manage')")
    public List<SiteService.Person> reorder(@RequestBody Map<String, List<Long>> body) {
        return site.reorder(body == null ? null : body.get("ids"));
    }
}
