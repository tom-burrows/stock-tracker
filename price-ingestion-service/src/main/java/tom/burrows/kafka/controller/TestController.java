package tom.burrows.kafka.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tom.burrows.events.Event;
import tom.burrows.kafka.Producer;

@Slf4j
@RequestMapping("/test/event")
@RestController
public class TestController {
    @Autowired
    private Producer producer;

    @PostMapping("/publish")
    public ResponseEntity<Event> post(@RequestBody Event event) {
        producer.publish(event);
        return ResponseEntity.ok(event);
    }

}
