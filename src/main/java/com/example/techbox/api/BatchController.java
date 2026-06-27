package com.example.techbox.api;

import com.example.techbox.api.dto.BatchLogResponse;
import com.example.techbox.batch.BatchJobService;
import com.example.techbox.batch.NotifierService;
import com.example.techbox.repository.BatchLogRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
@Slf4j
public class BatchController {

    private final BatchJobService batchJobService;
    private final NotifierService notifierService;
    private final BatchLogRepository batchLogRepository;

    @GetMapping("/status")
    public BatchLogResponse getStatus() {
        return batchLogRepository.findFirstByOrderByStartedAtDesc()
                .map(BatchLogResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("No batch log found"));
    }

    @GetMapping("/logs")
    public List<BatchLogResponse> getLogs(@RequestParam(defaultValue = "30") int limit) {
        return batchLogRepository.findRecentLogs(PageRequest.of(0, limit)).stream()
                .map(BatchLogResponse::from)
                .toList();
    }

    @PostMapping("/run")
    public ResponseEntity<Void> runBatch() {
        log.info("Manual batch run triggered");
        batchJobService.runBatch();
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/notify")
    public ResponseEntity<Void> sendNotification() {
        log.info("Manual LINE notification triggered");
        notifierService.sendDailyDigest();
        return ResponseEntity.accepted().build();
    }
}
