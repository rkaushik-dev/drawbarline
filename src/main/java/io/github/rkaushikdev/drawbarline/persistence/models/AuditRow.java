package io.github.rkaushikdev.drawbarline.persistence.models;

import io.github.rkaushikdev.drawbarline.limits.Limits;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One decision a person made, one row an entry and nothing else.
 *
 * <p>There is no foreign key on the subject, because a subject is a car one day and a period the
 * next, and a column that points at four different tables depending on a neighbouring column is
 * worse than a string. The action is stored by its name.
 */
@Entity
@Table(
        name = "audit",
        indexes = {
            @Index(name = "ix_audit_action", columnList = "action"),
            @Index(name = "ix_audit_actor_at", columnList = "actor, at"),
            @Index(name = "ix_audit_at", columnList = "at"),
            @Index(name = "ix_audit_subject", columnList = "subject")
        })
public class AuditRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "actor", length = Limits.ACTOR, nullable = false)
    private String actor;

    @Column(name = "action", length = 24, nullable = false)
    private String action;

    @Column(name = "subject", length = Limits.SUBJECT, nullable = false)
    private String subject;

    @Column(name = "detail", length = Limits.DETAIL, nullable = false)
    private String detail = "";

    protected AuditRow() {}

    public AuditRow(Instant at, String actor, String action, String subject, String detail) {
        this.at = at;
        this.actor = actor;
        this.action = action;
        this.subject = subject;
        this.detail = detail;
    }

    public Integer getId() {
        return id;
    }

    public Instant getAt() {
        return at;
    }

    public String getActor() {
        return actor;
    }

    /** The name of the action taken. */
    public String getAction() {
        return action;
    }

    public String getSubject() {
        return subject;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "<AuditRow " + actor + " " + action + " " + subject + ">";
    }
}
