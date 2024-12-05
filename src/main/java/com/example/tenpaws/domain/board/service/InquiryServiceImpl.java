package com.example.tenpaws.domain.board.service;

import com.example.tenpaws.domain.board.dto.request.InquiryRequest;
import com.example.tenpaws.domain.board.dto.response.CommentResponse;
import com.example.tenpaws.domain.board.dto.response.InquiryDetailResponse;
import com.example.tenpaws.domain.board.dto.response.InquiryListViewResponse;
import com.example.tenpaws.domain.board.dto.response.InquiryResponse;
import com.example.tenpaws.domain.board.entity.Inquiry;
import com.example.tenpaws.domain.board.repository.InquiryRepository;
import com.example.tenpaws.domain.notification.factory.NotificationFactory;
import com.example.tenpaws.domain.notification.service.NotificationService;
import com.example.tenpaws.domain.shelter.entity.Shelter;
import com.example.tenpaws.domain.shelter.repository.ShelterRepository;
import com.example.tenpaws.domain.user.entity.OAuth2UserEntity;
import com.example.tenpaws.domain.user.entity.User;
import com.example.tenpaws.domain.user.repositoty.OAuth2UserRepository;
import com.example.tenpaws.domain.user.repositoty.UserRepository;
import com.example.tenpaws.global.entity.UserRole;
import com.example.tenpaws.global.exception.BaseException;
import com.example.tenpaws.global.exception.ErrorCode;
import com.example.tenpaws.global.security.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InquiryServiceImpl implements InquiryService {
    private final UserRepository userRepository;
    private final OAuth2UserRepository oauth2UserRepository;
    private final ShelterRepository shelterRepository;
    private final InquiryRepository inquiryRepository;
    private final NotificationService notificationService;
    private final NotificationFactory notificationFactory;
    private final CustomUserDetailsService userDetailsService;

    @Override
    @Transactional
    public InquiryResponse create(InquiryRequest request, String email) {
        Map<String, Object> userInfo = userDetailsService.getInfosByEmail(email);
        UserRole role = (UserRole) userInfo.get("role");

        if (role != UserRole.ROLE_USER && role != UserRole.ROLE_SHELTER) {
            throw new BaseException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        Inquiry inquiry;
        if (role == UserRole.ROLE_USER) {
            // 일반 회원 또는 소셜 로그인 회원 처리
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> {
                        // 일반 회원에서 찾지 못한 경우 소셜 로그인 회원 확인
                        OAuth2UserEntity oauth2User = oauth2UserRepository.findByEmail(email)
                                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
                        return User.builder()
                                .email(oauth2User.getEmail())
                                .username(oauth2User.getUsername())
                                .userRole(UserRole.ROLE_USER)
                                .build();
                    });
            inquiry = inquiryRepository.save(request.toEntity(user));
        } else {
            // 보호소 회원 처리
            Shelter shelter = shelterRepository.findByEmail(email)
                    .orElseThrow(() -> new BaseException(ErrorCode.SHELTER_NOT_FOUND));
            inquiry = inquiryRepository.save(request.toEntity(shelter));
        }

        notificationService.create(
                notificationFactory.createInquirySubmittedNotification(email)
        );

        return new InquiryResponse(inquiry);
    }

    @Override
    public Page<InquiryListViewResponse> getList(Pageable pageable) {
        return inquiryRepository.findAll(pageable)
                .map(InquiryListViewResponse::new);
    }

    @Override
    @Transactional
    public InquiryDetailResponse findById(Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BaseException(ErrorCode.INQUIRY_NOT_FOUND));

        inquiry.incrementViewCount();

        List<CommentResponse> comments = inquiry.getComments().stream()
                .map(CommentResponse::new)
                .collect(Collectors.toList());

        return new InquiryDetailResponse(inquiry, comments);
    }

    @Override
    @Transactional
    public InquiryResponse update(Long inquiryId, InquiryRequest request) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BaseException(ErrorCode.INQUIRY_NOT_FOUND));
        inquiry.update(request.getTitle(), request.getContent());
        return new InquiryResponse(inquiry);
    }

    @Override
    @Transactional
    public void delete(Long inquiryId) {
        inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BaseException(ErrorCode.INQUIRY_NOT_FOUND));
        inquiryRepository.deleteById(inquiryId);
    }

    @Override
    public Page<InquiryListViewResponse> getMyList(String email, Pageable pageable) {
        return inquiryRepository.findByWriterEmail(email, pageable)
                .map(InquiryListViewResponse::new);
    }
}