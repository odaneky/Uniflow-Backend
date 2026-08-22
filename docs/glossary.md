# Glossary

Product words first. Code names in parentheses when they differ.

## Course

The catalog definition, e.g. **COMP2140 Database Systems**. It exists across terms. Students do not enrol in a course.

In code: `Course`.

## Occurrence (section)

This term’s offering of a course — what students enrol in. Coded **UN1**, **UN2**, and so on. Unique per course, not university-wide: COMP2140 and CIT2004 may both have UN1.

**Occurrence** is the product word. **Section** is the same thing in code and URLs (`CourseSection`, `sectionId`, `/courses/sections/...`).

An occurrence is a **set**. It can hold up to three components: lecture, tutorial, and lab. Extra tutorial+lab streams are further occurrences (UN2, UN3).

## Component

A teaching activity: **Lecture**, **Tutorial**, or **Lab**.

On the **course**, components are the activities the catalog item includes (COMP2140 may list lecture and tutorial).

On the **occurrence**, each selected component has its own seats, teacher, and timetable. They are not themselves occurrences.

In code: `CourseComponent` on the course; `SectionComponent` on the occurrence.

## Meeting

One timetable slot for a component: day, start, end, room. An occurrence’s lecture might meet Mon/Wed/Fri; its tutorial another day.

In code: `SectionMeeting`.

## Enrolment

A student’s seat in an **occurrence**, not in a course and not in a single component. Capacity and open/closed status live on the occurrence.

In code: `Enrollment` keyed by `courseSectionId`.

## Term

The academic period an occurrence belongs to, e.g. Semester 1 2026.

In code: `AcademicTerm`.

## Faculty

An academic division of the university, e.g. Faculty of Science. It contains **departments**.

This is not a person. Teaching staff are **lecturers** (and related roles).

In code: `Faculty`, `Department`. People are `User` records with a role such as `LECTURER`.

## Lecturer

A person who teaches. Assigned per component on an occurrence. The Faculty admin list is live lecturer accounts, not mock names.

## How they nest

```
Course          COMP2140 Database Systems
  components    Lecture, Tutorial, Lab
  Occurrence    UN1          ← section in code; students enrol here
    Component   Lecture      120 seats, a teacher, meetings
    Component   Tutorial      40 seats, a teacher, meetings
    Component   Lab           40 seats, a teacher, meetings
  Occurrence    UN2          ← another set (e.g. extra tutorial+lab, or a second lecture)
```
