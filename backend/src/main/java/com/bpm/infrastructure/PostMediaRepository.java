package com.bpm.infrastructure;

import com.bpm.domain.social.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMediaRepository extends JpaRepository<PostMedia, String> {
}
