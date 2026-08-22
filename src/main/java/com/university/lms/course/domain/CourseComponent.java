package com.university.lms.course.domain;

/**
 * A teaching activity the catalog course includes.
 *
 * <p>A course is not "a lecture". Most taught courses include more than one of these — lecture
 * and tutorial, sometimes laboratory. An occurrence (UN1) is a set that can hold up to three
 * of these. Meetings are the timetable for each component in that set.
 */
public enum CourseComponent {
    LECTURE,
    TUTORIAL,
    LABORATORY
}
