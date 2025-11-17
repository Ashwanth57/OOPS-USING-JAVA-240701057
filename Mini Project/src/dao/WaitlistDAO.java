package com.moviebooking.dao;

import com.moviebooking.model.Waitlist;
import com.moviebooking.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WaitlistDAO {

    /**
     * Add user to waitlist (FIFO Queue)
     */
    public boolean addToWaitlist(int userId, int showId, int requestedSeats) {
        Connection conn = null;
        CallableStatement stmt = null;

        try {
            conn = DBConnection.getConnection();

            // Call stored procedure
            String sql = "{call add_to_waitlist(?, ?, ?, ?)}";
            stmt = conn.prepareCall(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, showId);
            stmt.setInt(3, requestedSeats);
            stmt.registerOutParameter(4, Types.INTEGER);

            stmt.execute();

            int waitlistId = stmt.getInt(4);
            return waitlistId > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            closeResources(conn, stmt, null);
        }
    }

    /**
     * Get user's position in waitlist queue
     */
    public int getWaitlistPosition(int userId, int showId) {
        Connection conn = null;
        CallableStatement stmt = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "{? = call get_waitlist_position(?, ?)}";
            stmt = conn.prepareCall(sql);
            stmt.registerOutParameter(1, Types.INTEGER);
            stmt.setInt(2, userId);
            stmt.setInt(3, showId);

            stmt.execute();

            return stmt.getInt(1);

        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        } finally {
            closeResources(conn, stmt, null);
        }
    }

    /**
     * Check if user is in waitlist for a show
     */
    public boolean isUserInWaitlist(int userId, int showId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "SELECT COUNT(*) FROM waitlist WHERE user_id = ? AND show_id = ? AND status = 'Waiting'";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, showId);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return false;
    }

    /**
     * Get all waitlist entries for a show (ordered by queue position)
     */
    public List<Waitlist> getWaitlistByShow(int showId) {
        List<Waitlist> waitlist = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "SELECT * FROM waitlist WHERE show_id = ? AND status = 'Waiting' ORDER BY join_time ASC";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, showId);

            rs = stmt.executeQuery();
            while (rs.next()) {
                Waitlist wl = new Waitlist();
                wl.setWaitlistId(rs.getInt("waitlist_id"));
                wl.setUserId(rs.getInt("user_id"));
                wl.setShowId(rs.getInt("show_id"));
                wl.setRequestedSeats(rs.getInt("requested_seats"));
                wl.setJoinTime(rs.getTimestamp("join_time"));
                wl.setStatus(rs.getString("status"));
                wl.setPriorityScore(rs.getInt("priority_score"));
                wl.setNotificationSent(rs.getString("notification_sent"));
                wl.setExpiryTime(rs.getTimestamp("expiry_time"));

                waitlist.add(wl);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return waitlist;
    }

    /**
     * Get user's waitlist entries
     */
    public List<Waitlist> getUserWaitlist(int userId) {
        List<Waitlist> waitlist = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "SELECT * FROM waitlist WHERE user_id = ? AND status IN ('Waiting', 'Notified') ORDER BY join_time DESC";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);

            rs = stmt.executeQuery();
            while (rs.next()) {
                Waitlist wl = new Waitlist();
                wl.setWaitlistId(rs.getInt("waitlist_id"));
                wl.setUserId(rs.getInt("user_id"));
                wl.setShowId(rs.getInt("show_id"));
                wl.setRequestedSeats(rs.getInt("requested_seats"));
                wl.setJoinTime(rs.getTimestamp("join_time"));
                wl.setStatus(rs.getString("status"));
                wl.setPriorityScore(rs.getInt("priority_score"));
                wl.setNotificationSent(rs.getString("notification_sent"));
                wl.setExpiryTime(rs.getTimestamp("expiry_time"));

                waitlist.add(wl);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return waitlist;
    }

    /**
     * Remove user from waitlist (when they book or cancel)
     */
    public boolean removeFromWaitlist(int waitlistId) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "UPDATE waitlist SET status = 'Fulfilled' WHERE waitlist_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, waitlistId);

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            closeResources(conn, stmt, null);
        }
    }

    /**
     * Check for notified users (users who can now book)
     */
    public Waitlist getNotifiedWaitlist(int userId, int showId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "SELECT * FROM waitlist WHERE user_id = ? AND show_id = ? AND status = 'Notified' AND expiry_time > CURRENT_TIMESTAMP";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            stmt.setInt(2, showId);

            rs = stmt.executeQuery();
            if (rs.next()) {
                Waitlist wl = new Waitlist();
                wl.setWaitlistId(rs.getInt("waitlist_id"));
                wl.setUserId(rs.getInt("user_id"));
                wl.setShowId(rs.getInt("show_id"));
                wl.setRequestedSeats(rs.getInt("requested_seats"));
                wl.setJoinTime(rs.getTimestamp("join_time"));
                wl.setStatus(rs.getString("status"));
                wl.setExpiryTime(rs.getTimestamp("expiry_time"));

                return wl;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return null;
    }

    /**
     * Get waitlist count for a show
     */
    public int getWaitlistCount(int showId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "SELECT COUNT(*) FROM waitlist WHERE show_id = ? AND status = 'Waiting'";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, showId);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return 0;
    }

    /**
     * Clean expired notifications
     */
    public void cleanExpiredNotifications() {
        Connection conn = null;
        CallableStatement stmt = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "{call clean_expired_waitlist()}";
            stmt = conn.prepareCall(sql);
            stmt.execute();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, null);
        }
    }

    // Helper method to close resources
    private void closeResources(Connection conn, Statement stmt, ResultSet rs) {
        try {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}