package com.bpm.api;

import com.bpm.application.CollaborationService;
import com.bpm.domain.collab.Comment;
import com.bpm.domain.collab.CoordinationRound;
import com.bpm.domain.collab.Opinion;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Cộng tác trên tài liệu/hồ sơ (Story 3.11–3.15): bình luận, ý kiến phối hợp, phối hợp song song. */
@RestController
@RequestMapping("/api/v1/collab")
public class CollaborationController {

    private final CollaborationService svc;

    public CollaborationController(CollaborationService svc) {
        this.svc = svc;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    public record CommentReq(String body) {
    }

    public record OpinionReq(String stance, String body, String roundId) {
    }

    public record ResolveReq(String resolution, String note) {
    }

    public record CoordinationReq(List<String> participants, int deadlineHours) {
    }

    // ---- Bình luận (3.13/3.14) ----
    @GetMapping("/{type}/{id}/comments")
    public List<Comment> comments(@PathVariable String type, @PathVariable String id) {
        return svc.comments(type, id);
    }

    @PostMapping("/{type}/{id}/comments")
    public Comment addComment(@PathVariable String type, @PathVariable String id, @RequestBody CommentReq req, Authentication auth) {
        return svc.addComment(type, id, req.body(), actor(auth));
    }

    @PatchMapping("/comments/{cid}")
    public Comment editComment(@PathVariable String cid, @RequestBody CommentReq req, Authentication auth) {
        return svc.editComment(cid, req.body(), actor(auth));
    }

    // ---- Ý kiến phối hợp (3.11/3.12/3.14) ----
    @GetMapping("/{type}/{id}/opinions")
    public List<Opinion> opinions(@PathVariable String type, @PathVariable String id) {
        return svc.opinions(type, id);
    }

    @PostMapping("/{type}/{id}/opinions")
    public Opinion giveOpinion(@PathVariable String type, @PathVariable String id, @RequestBody OpinionReq req, Authentication auth) {
        return svc.giveOpinion(type, id, req.roundId(), req.stance(), req.body(), actor(auth));
    }

    @PatchMapping("/opinions/{oid}")
    public Opinion editOpinion(@PathVariable String oid, @RequestBody OpinionReq req, Authentication auth) {
        return svc.editOpinion(oid, req.stance(), req.body(), actor(auth));
    }

    @PostMapping("/opinions/{oid}/resolve")
    public Opinion resolve(@PathVariable String oid, @RequestBody ResolveReq req, Authentication auth) {
        return svc.resolveOpinion(oid, req.resolution(), req.note(), actor(auth));
    }

    // ---- Phối hợp song song (3.15) ----
    @PostMapping("/{type}/{id}/coordination")
    public CoordinationRound requestCoordination(@PathVariable String type, @PathVariable String id,
                                                 @RequestBody CoordinationReq req, Authentication auth) {
        return svc.requestCoordination(type, id, req.participants(), req.deadlineHours(), actor(auth));
    }

    @GetMapping("/{type}/{id}/rounds")
    public List<CoordinationRound> rounds(@PathVariable String type, @PathVariable String id) {
        return svc.rounds(type, id);
    }

    @GetMapping("/rounds/{rid}/status")
    public Map<String, Object> roundStatus(@PathVariable String rid) {
        return svc.roundStatus(rid);
    }

    @PostMapping("/rounds/{rid}/close")
    public void closeRound(@PathVariable String rid, Authentication auth) {
        svc.closeRound(rid, actor(auth));
    }
}
