package com.university.lms.course.domain;

import com.university.lms.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "section_meetings",
        uniqueConstraints = @UniqueConstraint(name = "uk_section_meetings", columnNames = {"section_id", "position"}))
@Getter
public class SectionMeeting extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "section_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_section_meetings_section"))
    private CourseSection section;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "room", nullable = false, length = 40)
    private String room;

    @Column(name = "session_type", nullable = false, length = 20)
    private String sessionType;

    @Column(name = "position", nullable = false)
    private int position;

    protected SectionMeeting() {}

    public SectionMeeting(
            CourseSection section,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            String room,
            String sessionType,
            int position) {
        this.section = section;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
        this.sessionType = sessionType;
        this.position = position;
    }
}
