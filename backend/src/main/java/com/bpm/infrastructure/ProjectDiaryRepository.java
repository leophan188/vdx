package com.bpm.infrastructure;

import com.bpm.domain.project.ProjectDiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectDiaryRepository extends JpaRepository<ProjectDiary, String> {

    /** Nhật ký của một dự án, mới → cũ (theo ngày làm việc rồi thời điểm tạo). */
    List<ProjectDiary> findByProjectIdOrderByWorkDateDescCreatedAtDesc(String projectId);

    void deleteByProjectId(String projectId);
}
