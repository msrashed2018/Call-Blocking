# Call-Blocking


## Running the Project  


**All needed binaries and libraries are available in the following links:**  
	1- https://github.com/msrashed2018/Call-Blocking/tree/master/lib  
	2- https://github.com/msrashed2018/Call-Blocking/releases  

### Steps  

**1.Ensure that postgresql service is running and database is created**   

	Database commands file : callblocking/data/callblocking-database-commands.sql

**2.Restcomm Media Server Configuration:**  

	a. Download restcomm media server 5.0.0.2 and unzip package
	b. Change IP bind address in file (Path: <install directory>/conf/ mediaserver.xml).
	c. Run the server 
		$ cd <install directory>/bin
		$./run.sh

**3.Restcomm SLEE wildfly configuration** 

	a. Deploy the following packages in server deployments directory (Path: <install directory>/wildfly-10.1.0.Final/standalone/deployments/)

		i. SIP RA : change bind IP Address (deactivate resource adaptor before changing IP bind address parameter then activate it again)    
		ii. MGCP RA : change bind IP Address (deactivate resource adaptor before changing IP bind address parameter then activate it again)  
		iii. JDBC RA  
		iv. Postgresql driver: (it used by wildfly datasource to interact with postgresql database server).  
		v. Announcement war file (it is web application contains an audio file which will be used by media server to be played on RTP channel between sip client and 			media server).  

	b. Start the server
		$ cd <install directory>/wildfly-10.1.0.Final/bin
		$./ standalone.sh –b=192.168.1.5 		 (type your machine ip address  instead)
	c. Adjust wildfly datasource 
	d. Deploy project jar file (Callblocking-1.0-final.jar) and start call blocking service 

**4. Download CSipSimple ( https://play.google.com/store/apps/details?id=com.csipsimple&hl=en ) on android phone. We used CSipSimple phone instead of linphone as it support audio codec that media server can decode it. So it will work in scenario of playing annoucement.**  

**5. Download linphone ( http://www.linphone.org ) on windows or centos machine.  Change username to be equal to any msisdn as you define in users profile in your database. You can use linphone for B2BUA scenario. It works well.**  

**6. Add users to database with their IP addresses so Application server can forward call.**  

**7. Insert users in black list and white list.**  

**8. Try to make two call, one for user exists in white list and another for user exists in black list as :**  
		SIP URL Format is written like:      
 			sip:[partyBmsisdn]@[application server Ip address]:5060  
	

 

