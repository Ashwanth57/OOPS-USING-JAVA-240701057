package com.moviebooking.dao;

import com.moviebooking.model.Movie;
import com.moviebooking.util.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MovieDAO {

    public List<Movie> getAllMovies() {
        List<Movie> movies = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            String sql = "SELECT * FROM movies WHERE status = 'Active' ORDER BY release_date DESC";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                movies.add(extractMovieFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return movies;
    }

    public List<Movie> searchMovies(String keyword) {
        List<Movie> movies = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            String sql = "SELECT * FROM movies WHERE status = 'Active' AND (LOWER(title) LIKE ? OR LOWER(genre) LIKE ?) ORDER BY title";
            stmt = conn.prepareStatement(sql);
            String searchPattern = "%" + keyword.toLowerCase() + "%";
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);

            rs = stmt.executeQuery();
            while (rs.next()) {
                movies.add(extractMovieFromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return movies;
    }

    public Movie getMovieById(int movieId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            String sql = "SELECT * FROM movies WHERE movie_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, movieId);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return extractMovieFromResultSet(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return null;
    }

    private Movie extractMovieFromResultSet(ResultSet rs) throws SQLException {
        Movie movie = new Movie();
        movie.setMovieId(rs.getInt("movie_id"));
        movie.setTitle(rs.getString("title"));
        movie.setDescription(rs.getString("description"));
        movie.setGenre(rs.getString("genre"));
        movie.setDuration(rs.getInt("duration"));
        movie.setRating(rs.getString("rating"));
        movie.setPosterUrl(rs.getString("poster_url"));
        movie.setReleaseDate(rs.getDate("release_date"));
        movie.setStatus(rs.getString("status"));
        return movie;
    }

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
