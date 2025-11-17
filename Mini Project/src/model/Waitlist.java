package com.moviebooking.model;

import java.sql.Timestamp;

public class Waitlist {
    private int waitlistId;
    private int userId;
    private int showId;
    private int requestedSeats;
    private Timestamp joinTime;
    private String status;
    private int priorityScore;
    private String notificationSent;
    private Timestamp expiryTime;

    // Constructors
    public Waitlist() {}

    public Waitlist(int userId, int showId, int requestedSeats) {
        this.userId = userId;
        this.showId = showId;
        this.requestedSeats = requestedSeats;
    }

    // Getters and Setters
    public int getWaitlistId() { return waitlistId; }
    public void setWaitlistId(int waitlistId) { this.waitlistId = waitlistId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getShowId() { return showId; }
    public void setShowId(int showId) { this.showId = showId; }

    public int getRequestedSeats() { return requestedSeats; }
    public void setRequestedSeats(int requestedSeats) { this.requestedSeats = requestedSeats; }

    public Timestamp getJoinTime() { return joinTime; }
    public void setJoinTime(Timestamp joinTime) { this.joinTime = joinTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getPriorityScore() { return priorityScore; }
    public void setPriorityScore(int priorityScore) { this.priorityScore = priorityScore; }

    public String getNotificationSent() { return notificationSent; }
    public void setNotificationSent(String notificationSent) { this.notificationSent = notificationSent; }

    public Timestamp getExpiryTime() { return expiryTime; }
    public void setExpiryTime(Timestamp expiryTime) { this.expiryTime = expiryTime; }
}