package com.moviebooking.model;

import java.sql.Timestamp;
import java.util.List;

public class Booking {
    private int bookingId;
    private int userId;
    private int showId;
    private Timestamp bookingDate;
    private double totalAmount;
    private String paymentStatus;  // 'Completed', 'Failed', 'Pending'
    private String bookingStatus;  // 'Active', 'Cancelled'
    private List<Integer> seatIds; // List of booked seat IDs

    // Constructors
    public Booking() {}

    public Booking(int userId, int showId, double totalAmount) {
        this.userId = userId;
        this.showId = showId;
        this.totalAmount = totalAmount;
        this.paymentStatus = "Pending";
        this.bookingStatus = "Active";
    }

    public Booking(int bookingId, int userId, int showId, Timestamp bookingDate,
                   double totalAmount, String paymentStatus, String bookingStatus) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.showId = showId;
        this.bookingDate = bookingDate;
        this.totalAmount = totalAmount;
        this.paymentStatus = paymentStatus;
        this.bookingStatus = bookingStatus;
    }

    // Getters and Setters
    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getShowId() {
        return showId;
    }

    public void setShowId(int showId) {
        this.showId = showId;
    }

    public Timestamp getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(Timestamp bookingDate) {
        this.bookingDate = bookingDate;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(String bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    public List<Integer> getSeatIds() {
        return seatIds;
    }

    public void setSeatIds(List<Integer> seatIds) {
        this.seatIds = seatIds;
    }

    // Utility Methods
    @Override
    public String toString() {
        return "Booking{" +
                "bookingId=" + bookingId +
                ", userId=" + userId +
                ", showId=" + showId +
                ", bookingDate=" + bookingDate +
                ", totalAmount=" + totalAmount +
                ", paymentStatus='" + paymentStatus + '\'' +
                ", bookingStatus='" + bookingStatus + '\'' +
                '}';
    }

    /**
     * Check if booking is active
     */
    public boolean isActive() {
        return "Active".equalsIgnoreCase(bookingStatus);
    }

    /**
     * Check if payment is completed
     */
    public boolean isPaymentCompleted() {
        return "Completed".equalsIgnoreCase(paymentStatus);
    }

    /**
     * Check if booking is cancelled
     */
    public boolean isCancelled() {
        return "Cancelled".equalsIgnoreCase(bookingStatus);
    }
}