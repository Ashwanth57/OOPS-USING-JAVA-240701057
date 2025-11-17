package com.moviebooking.model;

public class Seat {
    private int seatId;
    private int showId;
    private String seatRow;
    private int seatNumber;
    private String status;

    // Constructors
    public Seat() {}

    public Seat(int seatId, int showId, String seatRow, int seatNumber, String status) {
        this.seatId = seatId;
        this.showId = showId;
        this.seatRow = seatRow;
        this.seatNumber = seatNumber;
        this.status = status;
    }

    // Getters and Setters
    public int getSeatId() { return seatId; }
    public void setSeatId(int seatId) { this.seatId = seatId; }

    public int getShowId() { return showId; }
    public void setShowId(int showId) { this.showId = showId; }

    public String getSeatRow() { return seatRow; }
    public void setSeatRow(String seatRow) { this.seatRow = seatRow; }

    public int getSeatNumber() { return seatNumber; }
    public void setSeatNumber(int seatNumber) { this.seatNumber = seatNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSeatLabel() {
        return seatRow + seatNumber;
    }
}
