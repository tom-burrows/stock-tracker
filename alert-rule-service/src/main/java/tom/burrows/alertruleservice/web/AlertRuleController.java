package tom.burrows.alertruleservice.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tom.burrows.alertruleservice.domain.AlertRule;
import tom.burrows.alertruleservice.dto.AlertRuleResponse;
import tom.burrows.alertruleservice.dto.CreateAlertRuleRequest;
import tom.burrows.alertruleservice.service.AlertRuleService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleController {

    private final AlertRuleService service;

    public AlertRuleController(AlertRuleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AlertRuleResponse> create(@Valid @RequestBody CreateAlertRuleRequest request) {
        AlertRule created = service.create(request);
        return ResponseEntity.created(URI.create("/api/alert-rules/" + created.getId()))
                .body(AlertRuleResponse.from(created));
    }

    @GetMapping
    public List<AlertRuleResponse> listByUser(@RequestParam Long userId) {
        return service.listByUser(userId).stream().map(AlertRuleResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public AlertRuleResponse toggleActive(@PathVariable Long id) {
        return AlertRuleResponse.from(service.toggleActive(id));
    }
}
