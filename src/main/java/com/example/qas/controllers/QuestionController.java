package com.example.qas.controllers;

import com.example.qas.dto.request.AnswerRequest;
import com.example.qas.dto.request.QuestionRequest;
import com.example.qas.dto.response.QuestionResponse;
import com.example.qas.services.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    // === PATIENT asks a question ===

    @PostMapping("/{appointmentId}/questions")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<QuestionResponse> askQuestion(
            @PathVariable Long appointmentId,
            @Valid @RequestBody QuestionRequest request) {
        QuestionResponse response = questionService.askQuestion(appointmentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // === View a specific question (patient or doctor) ===

    @GetMapping("/{appointmentId}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<QuestionResponse> getQuestion(
            @PathVariable Long appointmentId,
            @PathVariable Long questionId) {
        QuestionResponse response = questionService.getQuestion(appointmentId, questionId);
        return ResponseEntity.ok(response);
    }

    // === Get all questions for an appointment (patient or doctor) ===

    @GetMapping("/{appointmentId}/questions")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR')")
    public ResponseEntity<List<QuestionResponse>> getQuestionsForAppointment(
            @PathVariable Long appointmentId) {
        List<QuestionResponse> responses = questionService.getQuestionsForAppointment(appointmentId);
        return ResponseEntity.ok(responses);
    }

    // === DOCTOR answers a question ===

    @PostMapping("/{appointmentId}/questions/{questionId}/answer")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<QuestionResponse> answerQuestion(
            @PathVariable Long appointmentId,
            @PathVariable Long questionId,
            @Valid @RequestBody AnswerRequest request) {
        QuestionResponse response = questionService.answerQuestion(appointmentId, questionId, request);
        return ResponseEntity.ok(response);
    }
}