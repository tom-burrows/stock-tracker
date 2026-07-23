package tom.burrows.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tom.burrows.notificationservice.domain.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}
