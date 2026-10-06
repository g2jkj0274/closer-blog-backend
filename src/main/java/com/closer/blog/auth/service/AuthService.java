package com.closer.blog.auth.service;

import com.closer.blog.auth.dto.SignupRequest;
import com.closer.blog.folder.service.FolderService;
import com.closer.blog.user.domain.User;
import com.closer.blog.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;

    private final FolderService folderService;

    /**
     * 사용자와 홈 폴더를 한 트랜잭션에서 만든다. 둘 중 하나라도 실패하면 둘 다 생기지 않는다.
     */
    @Transactional
    public User signup(SignupRequest request) {
        User user = userService.register(request.username(), request.email(), request.password(),
                Boolean.TRUE.equals(request.mailOptIn()));
        folderService.createHome(user);
        return user;
    }

}
