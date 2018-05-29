package com.iti.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.restcomm.slee.resource.jdbc.JdbcActivity;
import org.restcomm.slee.resource.jdbc.task.JdbcTaskContext;
import org.restcomm.slee.resource.jdbc.task.simple.SimpleJdbcTask;

import com.iti.callblocking.CallSbb;

public class CallBlockingDBMS {

    public static void main(String[] args) {

    }

    public boolean HasWhiteList = false;
    public boolean IsWhiteTimeFrame = false;
    public boolean InWhiteList = false;
    public boolean HasBlackList = false;
    public boolean InBlackList = false;
    public boolean IsBlackTimeFrame = false;
    public String address=null;

    
    public SimpleJdbcTask getUserAddress(final int MSISDN) {
    	SimpleJdbcTask task = new SimpleJdbcTask() {
            @Override
            public Object executeSimple(JdbcTaskContext context) {
                try {
                    Connection connection = context.getConnection();
                    PreparedStatement preparedStatement = connection
                            .prepareStatement("select address from users where msisdn=?");
                    preparedStatement.setInt(1, MSISDN);
                    preparedStatement.execute();
                    ResultSet resultSet = preparedStatement.getResultSet();
                    resultSet.next();
                    address=resultSet.getString(1);
                    return address;
                } catch (Exception e) {
                    return false;
                }
            }
        };

        // execute the task on the jdbc activity
        
    	return task;
    	
    }
    
    public SimpleJdbcTask HasWhiteList(final int MSISDN) {
        SimpleJdbcTask task = new SimpleJdbcTask() {
            @Override
            public Object executeSimple(JdbcTaskContext context) {
                try {
                    Connection connection = context.getConnection();
                    PreparedStatement preparedStatement = connection
                            .prepareStatement("select * from white where msisdn=?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, MSISDN);
                    preparedStatement.execute();

                    ResultSet rs = preparedStatement.getResultSet();

                    if (rs.next()) {

                        HasWhiteList = true;
                    } else {
                        HasWhiteList = false;
                    }
                    return "HasWhiteList="+HasWhiteList;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        
        return task;
    }

    public SimpleJdbcTask IsWhiteTimeFrame(final int MSISDN) {
        SimpleJdbcTask task = new SimpleJdbcTask() {
            @Override
            public Object executeSimple(JdbcTaskContext context) {
                try {
                    Timestamp t = new Timestamp(System.currentTimeMillis());
                    Connection connection = context.getConnection();
                    PreparedStatement preparedStatement = connection
                            .prepareStatement("select * from white where msisdn=?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, MSISDN);
                    preparedStatement.execute();

                    ResultSet rs = preparedStatement.getResultSet();

                    if (rs.next()) {
                        Timestamp white_start = rs.getTimestamp("start_time");
                        Timestamp white_end = rs.getTimestamp("end_time");
                        if (t.after(white_start) && t.before(white_end)) {
                            IsWhiteTimeFrame = true;
                        } else {
                            IsWhiteTimeFrame = false;
                        }
                    } else {
                        IsWhiteTimeFrame = false;
                    }

                    return "IsWhiteTimeFrame="+IsWhiteTimeFrame;
                } catch (Exception e) {
                    return false;
                }
            }
        };

        return task;
    }

    public SimpleJdbcTask InWhiteList(final int MSISDN_called, final String MSISDN_caller) {

        SimpleJdbcTask task = new SimpleJdbcTask() {
            @Override
            public Object executeSimple(JdbcTaskContext context) {
                try {
                    Timestamp t = new Timestamp(System.currentTimeMillis());
                    Connection connection = context.getConnection();
                    PreparedStatement preparedStatement = connection
                            .prepareStatement("select * from white_white_numbers,white where white.id=white_white_numbers.id and white.msisdn=? and white_numbers=?"
                                    + "", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, MSISDN_called);
                    preparedStatement.setString(2, MSISDN_caller);

                    preparedStatement.execute();

                    ResultSet rs = preparedStatement.getResultSet();
                    if (rs.next()) {
                        InWhiteList = true;
                    } else {
                        InWhiteList = false;
                    }
                    return "InWhiteList="+InWhiteList;
                } catch (Exception e) {
                    return false;
                }
            }
        };

        return task;
    }

    public SimpleJdbcTask HasBlackList(final int MSISDN) {
        SimpleJdbcTask task = new SimpleJdbcTask() {
            @Override
            public Object executeSimple(JdbcTaskContext context) {
                try {
                    Connection connection = context.getConnection();
                    PreparedStatement preparedStatement = connection
                            .prepareStatement("select * from black where msisdn=?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, MSISDN);
                    preparedStatement.execute();

                    ResultSet rs = preparedStatement.getResultSet();

                    if (rs.next()) {
                        HasBlackList = true;
                    } else {
                        HasBlackList = false;
                    }

                    return "HasBlackList="+HasBlackList;
                } catch (Exception e) {

                    return false;
                }
            }
        };
        return task;
    }

    public SimpleJdbcTask IsBlackTimeFrame(final int MSISDN) {
        SimpleJdbcTask task = new SimpleJdbcTask() {
            @Override
            public Object executeSimple(JdbcTaskContext context) {
                try {
                    Timestamp t = new Timestamp(System.currentTimeMillis());
                    Connection connection = context.getConnection();
                    PreparedStatement preparedStatement = connection
                            .prepareStatement("select * from black where msisdn=?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, MSISDN);
                    preparedStatement.execute();

                    ResultSet rs = preparedStatement.getResultSet();
                    if (rs.next()) {
                        Timestamp black_start = rs.getTimestamp("start_time");
                        Timestamp black_end = rs.getTimestamp("end_time");
                        if (t.after(black_start) && t.before(black_end)) {
                            IsBlackTimeFrame = true;
                        } else {
                            IsBlackTimeFrame = false;
                        }
                    } else {
                        IsBlackTimeFrame = false;
                    }

                    return "IsBlackTimeFrame="+IsBlackTimeFrame;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        
        return task;
    }

    public SimpleJdbcTask InBlackList(final int MSISDN_called, final String MSISDN_caller) {

        SimpleJdbcTask task = new SimpleJdbcTask() {
            @Override
            public Object executeSimple(JdbcTaskContext context) {
                try {
                    Timestamp t = new Timestamp(System.currentTimeMillis());
                    Connection connection = context.getConnection();
                    PreparedStatement preparedStatement = connection
                            .prepareStatement("select * from black_black_numbers,black where black.id=black_black_numbers.id and black.msisdn=? and black_numbers=?"
                                    + "", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, MSISDN_called);
                    preparedStatement.setString(2, MSISDN_caller);

                    preparedStatement.execute();
                    ResultSet rs = preparedStatement.getResultSet();

                    if (rs.next()) {
                        InBlackList = true;
                    } else {
                        InBlackList = false;
                    }
                    return "InBlackList="+InBlackList;
                } catch (Exception e) {

                    return false;
                }
            }
        };

        return task;
    }
}
