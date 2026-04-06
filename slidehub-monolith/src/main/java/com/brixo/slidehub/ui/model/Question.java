package com.brixo.slidehub.ui.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Pregunta enviada por un viewer del stream durante una sesión activa.
 *
 * viewer_token es una referencia suelta (sin FK) porque los viewers son efímeros (Redis).
 * display_name se cachea en el momento del envío; es null cuando anonymous=true.
 */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private PresentationSession session;

    @Column(name = "viewer_token", length = 120)
    private String viewerToken;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private QuestionStatus status = QuestionStatus.PENDING;

    @Column(name = "upvotes", nullable = false)
    private int upvotes = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Question() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public PresentationSession getSession() { return session; }
    public void setSession(PresentationSession session) { this.session = session; }

    public String getViewerToken() { return viewerToken; }
    public void setViewerToken(String viewerToken) { this.viewerToken = viewerToken; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public QuestionStatus getStatus() { return status; }
    public void setStatus(QuestionStatus status) { this.status = status; }

    public int getUpvotes() { return upvotes; }
    public void setUpvotes(int upvotes) { this.upvotes = upvotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
