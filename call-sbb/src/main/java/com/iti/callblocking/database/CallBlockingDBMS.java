package com.iti.callblocking.database;

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


    public boolean HasWhiteList;
    public boolean IsWhiteTimeFrame;
    public boolean InWhiteList;
    public boolean HasBlackList;
    public boolean InBlackList ;
    public boolean IsBlackTimeFrame ;
    public String address;

    public CallBlockingDBMS(){
    	HasWhiteList = false;
    	IsWhiteTimeFrame = false;
    	InWhiteList = false;
    	HasBlackList = false;
    	InBlackList = false;
    	IsBlackTimeFrame = false;
    	address=null;
    }
    
    public SimpleJdbcTask processParties(final String AParty, final int BParty) {
    	SimpleJdbcTask task = new SimpleJdbcTask() {
            @Override
            public Object executeSimple(JdbcTaskContext context) {
                try {
                	PreparedStatement preparedStatement=null;
                	ResultSet rs1,rs2,rs3,rs4,rs5=null;
                	Timestamp currentTime = new Timestamp(System.currentTimeMillis());
                	String addressQuery="select address from users where msisdn=?";
                	String whiteListQuery="select * from white where msisdn=?";
                	String InWhiteListQuery="select * from white_white_numbers,white where white.id=white_white_numbers.id and white.msisdn=? and white_numbers=?";
                	String blackListQuery="select * from black where msisdn=?";
                	String InBlackListQuery="select * from black_black_numbers,black where black.id=black_black_numbers.id and black.msisdn=? and black_numbers=?";
                	
                	Connection connection = context.getConnection();
                	
                    //Bparty Ip Address
                    preparedStatement = connection
                            .prepareStatement(addressQuery);
                    preparedStatement.setInt(1, BParty);
                    preparedStatement.execute();
                    rs1 = preparedStatement.getResultSet();
                    rs1.next();
                    address=rs1.getString(1);
                    
                    //WhiteList Query
                	preparedStatement = connection
                            .prepareStatement(whiteListQuery, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, BParty);
                    preparedStatement.execute();

                    rs2 = preparedStatement.getResultSet();

                    if (rs2.next()) {
                    	HasWhiteList = true;
                    	Timestamp white_start = rs2.getTimestamp("start_time");
                        Timestamp white_end = rs2.getTimestamp("end_time");
                        if (currentTime.after(white_start) && currentTime.before(white_end)) {
                            IsWhiteTimeFrame = true;
                        } 
                    }
                    
                    //in WhiteList Query
                    preparedStatement = connection.prepareStatement(InWhiteListQuery, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, BParty);
                    preparedStatement.setString(2, AParty);

                    preparedStatement.execute();
                    rs3 = preparedStatement.getResultSet();
                    if (rs3.next()) {
                        InWhiteList = true;
                    } 
                	
                    //black list Query
                    preparedStatement = connection
                            .prepareStatement(blackListQuery, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, BParty);
                    preparedStatement.execute();

                    rs4 = preparedStatement.getResultSet();

                    if (rs4.next()) {
                    	HasBlackList = true;
                        Timestamp black_start = rs4.getTimestamp("start_time");
                        Timestamp black_end = rs4.getTimestamp("end_time");
                        if (currentTime.after(black_start) && currentTime.before(black_end)) {
                            IsBlackTimeFrame = true;
                        } 
                    }
                    
                    //in Black List Query
                    preparedStatement = connection
                            .prepareStatement(InBlackListQuery, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
                    preparedStatement.setInt(1, BParty);
                    preparedStatement.setString(2, AParty);

                    preparedStatement.execute();
                    rs5 = preparedStatement.getResultSet();

                    if (rs5.next()) {
                        InBlackList = true;
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        return task;
    }
    
   
}
