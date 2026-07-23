package tom.burrows.notificationservice.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tom.burrows.notificationservice.dto.NotificationPayload;
import tom.burrows.notificationservice.service.NotificationService;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationPayload> listByUser(@RequestParam Long userId) {
        return service.listByUser(userId).stream().map(NotificationPayload::from).toList();
    }
}
