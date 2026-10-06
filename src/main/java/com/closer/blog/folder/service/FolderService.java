package com.closer.blog.folder.service;

import java.time.Clock;

import com.closer.blog.folder.domain.Folder;
import com.closer.blog.folder.domain.FolderRepository;
import com.closer.blog.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;

    private final Clock clock;

    @Transactional
    public Folder createHome(User owner) {
        return folderRepository.saveAndFlush(Folder.home(owner, clock.instant()));
    }

}
