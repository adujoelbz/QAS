package com.example.qas.services;

import com.example.qas.dto.request.AnswerRequest;
import com.example.qas.dto.request.QuestionRequest;
import com.example.qas.dto.response.QuestionResponse;
import com.example.qas.models.Appointment;
import com.example.qas.models.Doctor;
import com.example.qas.models.Patient;
import com.example.qas.models.Question;
import com.example.qas.repositories.AppointmentRepository;
import com.example.qas.repositories.DoctorRepository;
import com.example.qas.repositories.PatientRepository;
import com.example.qas.repositories.QuestionRepository;
import com.example.qas.security.SecurityUtils;
import com.example.qas.models.User;
import com.example.qas.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    // === Patient asks a question ===

    @Transactional
    public QuestionResponse askQuestion(Long appointmentId, QuestionRequest request) {
        Patient currentPatient = getCurrentPatient();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Verify patient owns this appointment
        if (!appointment.getPatient().getId().equals(currentPatient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this appointment");
        }

        // The doctor must be assigned (approved appointment)
        if (appointment.getDoctor() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No doctor assigned to this appointment yet");
        }

        Question question = new Question();
        question.setAppointment(appointment);
        question.setPatient(currentPatient);
        question.setDoctor(appointment.getDoctor());
        question.setQuestion(request.getQuestion());

        question = questionRepository.save(question);
        return mapToResponse(question);
    }

    // === Get a question by ID (patient or doctor can view) ===

    public QuestionResponse getQuestion(Long appointmentId, Long questionId) {
        // Check if the appointment exists
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        Question question = questionRepository.findByIdAndAppointmentId(questionId, appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        // Verify the current user is either the patient or the doctor for this appointment
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        if (!currentUser.getId().equals(appointment.getPatient().getUser().getId()) &&
                !currentUser.getId().equals(appointment.getDoctor().getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to view this question");
        }

        return mapToResponse(question);
    }

    // === Get all questions for an appointment (patient or doctor) ===

    public List<QuestionResponse> getQuestionsForAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        if (!currentUser.getId().equals(appointment.getPatient().getUser().getId()) &&
                !currentUser.getId().equals(appointment.getDoctor().getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to view these questions");
        }

        return questionRepository.findByAppointmentId(appointmentId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // === Doctor answers a question ===

    @Transactional
    public QuestionResponse answerQuestion(Long appointmentId, Long questionId, AnswerRequest request) {
        Doctor currentDoctor = getCurrentDoctor();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Verify doctor is assigned to this appointment
        if (appointment.getDoctor() == null || !appointment.getDoctor().getId().equals(currentDoctor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not assigned to this appointment");
        }

        Question question = questionRepository.findByIdAndAppointmentId(questionId, appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        // Prevent answering if already answered
        if (question.getAnswer() != null && !question.getAnswer().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This question already has an answer");
        }

        question.setAnswer(request.getAnswer());
        question.setAnsweredAt(OffsetDateTime.now());
        question = questionRepository.save(question);

        // Optionally send notification to patient (we'll skip for now)

        return mapToResponse(question);
    }

    // === Helper: map entity to response DTO ===

    private QuestionResponse mapToResponse(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .appointmentId(question.getAppointment().getId())
                .patientId(question.getPatient().getId())
                .patientName(question.getPatient().getFirstName() + " " + question.getPatient().getLastName())
                .doctorId(question.getDoctor().getId())
                .doctorName(question.getDoctor().getFirstName() + " " + question.getDoctor().getLastName())
                .question(question.getQuestion())
                .answer(question.getAnswer())
                .answeredAt(question.getAnsweredAt())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }

    // === Helper methods ===

    private Patient getCurrentPatient() {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        return patientRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
    }

    private Doctor getCurrentDoctor() {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        return doctorRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
    }
}